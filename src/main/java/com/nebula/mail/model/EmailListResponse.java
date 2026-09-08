package com.nebula.mail.model;

import java.util.List;

/**
 * Paginated response containing a list of email summaries and mailbox metadata.
 */
public record EmailListResponse(
        List<EmailSummary> emails,
        String nextPageToken,
        int resultSizeEstimate,
        long unreadCount,
        String folder
) {}
