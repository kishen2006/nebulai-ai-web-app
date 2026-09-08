package com.nebula.mail.ai.model;

import java.util.List;
import java.util.Map;

/**
 * Contextual state sent from the frontend to give the AI assistant situational awareness.
 */
public record UiContext(
        String currentFolder,
        String selectedEmailId,
        String selectedEmailSubject,
        String selectedEmailFrom,
        String selectedEmailSnippet,
        String selectedEmailBody,
        boolean isComposeOpen,
        List<Map<String, Object>> visibleEmails
) {}
