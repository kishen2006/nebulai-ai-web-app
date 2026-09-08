package com.nebula.mail.controller;

import com.nebula.mail.config.AppProperties;
import com.nebula.mail.model.AuthStatus;
import com.nebula.mail.model.UserProfile;
import com.nebula.mail.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller handling Google OAuth 2.0 authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final GoogleAuthService authService;
    private final AppProperties appProperties;

    public AuthController(GoogleAuthService authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    /**
     * Checks current authentication and environment configuration status.
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatus> getStatus(HttpSession session) {
        boolean configured = appProperties.isGoogleOAuthConfigured();
        UserProfile profile = authService.getUserProfile(session);

        if (profile != null && profile.email() != null) {
            return ResponseEntity.ok(AuthStatus.authenticated(profile));
        }
        return ResponseEntity.ok(AuthStatus.unauthenticated(configured));
    }

    /**
     * Initiates Google OAuth 2.0 flow by redirecting the user to Google's consent screen.
     */
    @GetMapping("/google")
    public void initiateGoogleLogin(HttpServletResponse response) throws IOException {
        if (!appProperties.isGoogleOAuthConfigured()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    "Google OAuth is not configured. Please supply GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env");
            return;
        }

        String authUrl = authService.buildAuthorizationUrl();
        log.info("Redirecting user to Google OAuth URL...");
        response.sendRedirect(authUrl);
    }

    /**
     * Handles Google OAuth 2.0 callback with authorization code.
     */
    @GetMapping("/google/callback")
    public void handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session,
            HttpServletResponse response
    ) throws IOException {
        if (error != null) {
            log.error("Google OAuth error callback: {}", error);
            response.sendRedirect("/?error=" + URLEncoder.encode("Google OAuth error: " + error, StandardCharsets.UTF_8));
            return;
        }

        if (code == null || code.isBlank()) {
            log.error("Missing authorization code in OAuth callback.");
            response.sendRedirect("/?error=" + URLEncoder.encode("Missing authorization code", StandardCharsets.UTF_8));
            return;
        }

        try {
            UserProfile profile = authService.exchangeCodeAndCreateSession(code, session);
            log.info("OAuth completed successfully for {}", profile.email());
            response.sendRedirect("/?login=success");
        } catch (Exception ex) {
            log.error("Failed to exchange OAuth code for tokens", ex);
            response.sendRedirect("/?error=" + URLEncoder.encode("Authentication failed: " + ex.getMessage(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Clears user session and logs out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }
}
