package com.nebula.mail.ai.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Chat request from the user containing prompt, conversation history, and current UI context.
 */
public record ChatRequest(
        @NotBlank(message = "Message prompt cannot be empty")
        String message,
        UiContext context,
        List<Map<String, String>> history
) {}
