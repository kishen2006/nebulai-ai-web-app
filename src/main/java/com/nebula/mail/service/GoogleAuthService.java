package com.nebula.mail.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Profile;
import com.nebula.mail.config.AppProperties;
import com.nebula.mail.model.UserProfile;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Google OAuth 2.0 authorization code flow, token exchange,
 * automatic token refreshing, and user session binding.
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    public static final String SESSION_CREDENTIAL_KEY = "GMAIL_CREDENTIAL";
    public static final String SESSION_USER_PROFILE_KEY = "USER_PROFILE";

    // Scopes needed for mail reading, sending, modifying flags, and user profile info
    private static final List<String> SCOPES = Arrays.asList(
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_SEND,
            GmailScopes.GMAIL_MODIFY,
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
    );

    private final AppProperties appProperties;
    private final NetHttpTransport netHttpTransport;
    private final JsonFactory jsonFactory;

    // Cache of active user credentials for background sync services (keyed by email)
    private final Map<String, Credential> activeCredentials = new ConcurrentHashMap<>();

    public GoogleAuthService(AppProperties appProperties, NetHttpTransport netHttpTransport, JsonFactory jsonFactory) {
        this.appProperties = appProperties;
        this.netHttpTransport = netHttpTransport;
        this.jsonFactory = jsonFactory;
    }

    /**
     * Builds the Google OAuth 2.0 authorization consent URL.
     */
    public String buildAuthorizationUrl() {
        if (!appProperties.isGoogleOAuthConfigured()) {
            throw new IllegalStateException("Google OAuth credentials (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET) are missing.");
        }

        return new GoogleAuthorizationCodeRequestUrl(
                appProperties.getGoogleClientId(),
                appProperties.getGoogleRedirectUri(),
                SCOPES
        )
                .setAccessType("offline") // Request refresh_token
                .set("prompt", "consent") // Force consent to ensure refresh_token is returned
                .build();
    }

    /**
     * Exchanges the authorization code received from Google for access & refresh tokens.
     */
    public UserProfile exchangeCodeAndCreateSession(String code, HttpSession session) throws IOException {
        log.info("Exchanging OAuth authorization code for Google access/refresh tokens...");

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                netHttpTransport,
                jsonFactory,
                TOKEN_SERVER_URL,
                appProperties.getGoogleClientId(),
                appProperties.getGoogleClientSecret(),
                code,
                appProperties.getGoogleRedirectUri()
        ).execute();

        Credential credential = createCredential(tokenResponse);
        session.setAttribute(SESSION_CREDENTIAL_KEY, credential);

        // Fetch Gmail user profile
        Gmail gmail = new Gmail.Builder(netHttpTransport, jsonFactory, credential)
                .setApplicationName("Nebula-Mail")
                .build();

        Profile profile = gmail.users().getProfile("me").execute();
        String userEmail = profile.getEmailAddress();

        UserProfile userProfile = new UserProfile(
                userEmail,
                userEmail, // Fallback display name to email
                null,
                profile.getHistoryId() != null ? profile.getHistoryId().toString() : null,
                profile.getMessagesTotal() != null ? profile.getMessagesTotal() : 0L
        );

        session.setAttribute(SESSION_USER_PROFILE_KEY, userProfile);
        activeCredentials.put(userEmail, credential);

        log.info("User successfully authenticated via Google OAuth: {}", userEmail);
        return userProfile;
    }

    /**
     * Constructs a Credential instance configured to automatically refresh expired tokens.
     */
    public Credential createCredential(GoogleTokenResponse tokenResponse) {
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(netHttpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl(TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(
                        appProperties.getGoogleClientId(),
                        appProperties.getGoogleClientSecret()
                ))
                .build();

        credential.setAccessToken(tokenResponse.getAccessToken());
        if (tokenResponse.getRefreshToken() != null) {
            credential.setRefreshToken(tokenResponse.getRefreshToken());
        }
        if (tokenResponse.getExpiresInSeconds() != null) {
            credential.setExpiresInSeconds(tokenResponse.getExpiresInSeconds());
        }
        return credential;
    }

    /**
     * Retrieves the current user's Google Credential from the HTTP session.
     */
    public Credential getCredential(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (Credential) session.getAttribute(SESSION_CREDENTIAL_KEY);
    }

    /**
     * Retrieves the current user's UserProfile from the HTTP session.
     */
    public UserProfile getUserProfile(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (UserProfile) session.getAttribute(SESSION_USER_PROFILE_KEY);
    }

    /**
     * Retrieves an active credential by email for background tasks.
     */
    public Credential getActiveCredentialByEmail(String email) {
        return activeCredentials.get(email);
    }

    /**
     * Clears user session and logs out.
     */
    public void logout(HttpSession session) {
        if (session != null) {
            UserProfile profile = getUserProfile(session);
            if (profile != null && profile.email() != null) {
                activeCredentials.remove(profile.email());
            }
            session.invalidate();
        }
    }
}
