package com.nebula.mail.model;

import java.util.List;

/**
 * Lightweight representation of an email for list views (Inbox, Sent).
 */
public record EmailSummary(
        String id,
        String threadId,
        String from,
        String to,
        String subject,
        String snippet,
        String date,
        long internalDate,
        boolean isUnread,
        List<String> labels
) {}
