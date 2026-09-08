package com.nebula.mail.model;

/**
 * Status payload returned to frontend to check authentication and configuration status.
 */
public record AuthStatus(
        boolean authenticated,
        boolean configured,
        UserProfile user,
        String message
) {
    public static AuthStatus authenticated(UserProfile profile) {
        return new AuthStatus(true, true, profile, "Authenticated successfully.");
    }

    public static AuthStatus unauthenticated(boolean isConfigured) {
        String msg = isConfigured
                ? "Not authenticated. Please connect your Gmail account."
                : "Google OAuth is not configured. Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env file.";
        return new AuthStatus(false, isConfigured, null, msg);
    }
}
