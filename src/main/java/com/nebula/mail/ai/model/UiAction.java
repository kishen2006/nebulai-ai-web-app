package com.nebula.mail.ai.model;

import java.util.Map;

/**
 * A UI action commanded by the AI assistant to be executed by the frontend.
 */
public record UiAction(
        String action, // e.g. NAVIGATE_FOLDER, FILTER_EMAILS, OPEN_EMAIL, POPULATE_COMPOSE, MARK_READ
        Map<String, Object> parameters
) {}
