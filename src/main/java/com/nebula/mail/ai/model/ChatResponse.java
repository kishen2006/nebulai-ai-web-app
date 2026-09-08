package com.nebula.mail.ai.model;

import java.util.List;

/**
 * Chat response returned to frontend containing natural language reply and UI actions to execute.
 */
public record ChatResponse(
        String reply,
        List<UiAction> actions,
        String suggestedFollowUp
) {
    public static ChatResponse textOnly(String reply) {
        return new ChatResponse(reply, List.of(), null);
    }
}
