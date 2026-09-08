package com.nebula.mail.model;

import java.util.List;

/**
 * Full detail representation of an email message, including parsed HTML and text body.
 */
public record EmailDetail(
        String id,
        String threadId,
        String from,
        String to,
        String cc,
        String subject,
        String date,
        long internalDate,
        String bodyText,
        String bodyHtml,
        String snippet,
        boolean isUnread,
        List<String> labels
) {}
