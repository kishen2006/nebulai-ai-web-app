package com.nebula.mail.controller;

import com.nebula.mail.ai.model.ChatRequest;
import com.nebula.mail.ai.model.ChatResponse;
import com.nebula.mail.service.AiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing AI assistant chat endpoint with LLM Tool Calling.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Processes AI assistant queries with current UI context and native function calling.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received AI chat prompt: '{}'", request.message());
        ChatResponse response = aiService.processChat(request);
        return ResponseEntity.ok(response);
    }
}
