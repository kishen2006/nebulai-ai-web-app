package com.nebula.mail.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates search and filter criteria and translates them into Gmail search syntax.
 */
public record SearchFilter(
        String folder,
        String keyword,
        String sender,
        String dateFrom,
        String dateTo,
        Boolean unreadOnly
) {

    /**
     * Translates structured filter criteria into standard Gmail search query syntax.
     */
    public String toGmailQuery() {
        List<String> parts = new ArrayList<>();

        if (folder != null && !folder.isBlank()) {
            if ("SENT".equalsIgnoreCase(folder)) {
                parts.add("label:SENT");
            } else {
                parts.add("label:INBOX");
            }
        }

        if (sender != null && !sender.isBlank()) {
            parts.add("from:(" + sender.trim() + ")");
        }

        if (unreadOnly != null && unreadOnly) {
            parts.add("is:unread");
        }

        if (dateFrom != null && !dateFrom.isBlank()) {
            parts.add("after:" + dateFrom.trim().replace('-', '/'));
        }

        if (dateTo != null && !dateTo.isBlank()) {
            parts.add("before:" + dateTo.trim().replace('-', '/'));
        }

        if (keyword != null && !keyword.isBlank()) {
            parts.add(keyword.trim());
        }

        return String.join(" ", parts);
    }
}
