package com.nebula.mail.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for replying to an email thread.
 */
public record ReplyEmailRequest(
        @NotBlank(message = "Message ID is required to reply")
        String emailId,
        String threadId,
        @NotBlank(message = "Recipient 'to' address is required")
        String to,
        String subject,
        @NotBlank(message = "Reply body is required")
        String body,
        boolean isHtml
) {}
