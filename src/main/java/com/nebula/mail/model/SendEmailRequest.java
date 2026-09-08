package com.nebula.mail.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for composing and sending a new email.
 */
public record SendEmailRequest(
        @NotBlank(message = "Recipient 'to' address is required")
        String to,
        String cc,
        @NotBlank(message = "Subject is required")
        String subject,
        @NotBlank(message = "Email body is required")
        String body,
        boolean isHtml
) {}
