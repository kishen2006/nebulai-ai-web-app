package com.nebula.mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Encapsulates application properties injected from environment variables or .env.
 */
@Configuration
public class AppProperties {

    @Value("${google.oauth.client-id}")
    private String googleClientId;

    @Value("${google.oauth.client-secret}")
    private String googleClientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String googleRedirectUri;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getGoogleClientSecret() {
        return googleClientSecret;
    }

    public String getGoogleRedirectUri() {
        return googleRedirectUri;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public boolean isGoogleOAuthConfigured() {
        return googleClientId != null && !googleClientId.isBlank()
                && googleClientSecret != null && !googleClientSecret.isBlank();
    }

    public boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
