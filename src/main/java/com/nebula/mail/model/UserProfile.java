package com.nebula.mail.model;

/**
 * Encapsulates authenticated user profile and mailbox state.
 */
public record UserProfile(
        String email,
        String displayName,
        String photoUrl,
        String historyId,
        Long messagesTotal
) {
    public static UserProfile unauthenticated() {
        return new UserProfile(null, null, null, null, 0L);
    }
}
