package com.nebula.mail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.mail.ai.AiToolRegistry;
import com.nebula.mail.ai.model.ChatRequest;
import com.nebula.mail.ai.model.ChatResponse;
import com.nebula.mail.ai.model.UiAction;
import com.nebula.mail.ai.model.UiContext;
import com.nebula.mail.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service orchestrating LLM interactions with Google Gemini,
 * using native tool/function calling to understand user intent and control the UI.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String GEMINI_API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final AppProperties appProperties;
    private final AiToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AiService(AppProperties appProperties, AiToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Processes a user chat message with context awareness and Gemini native function calling.
     */
    public ChatResponse processChat(ChatRequest request) {
        if (!appProperties.isGeminiConfigured()) {
            return ChatResponse.textOnly(
                    "⚠️ Gemini API Key is not configured. Please add your GEMINI_API_KEY in the .env file to enable the AI assistant."
            );
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequestBody(request);
            String url = String.format(GEMINI_API_URL_TEMPLATE, appProperties.getGeminiModel(), appProperties.getGeminiApiKey());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseGeminiResponse(response.getBody(), request.context());
            } else {
                log.error("Gemini API error: HTTP {}", response.getStatusCode());
                return ChatResponse.textOnly("Sorry, I received an error from the AI service. Please check your API key.");
            }
        } catch (Exception ex) {
            log.error("Failed to invoke Gemini API: {}", ex.getMessage(), ex);
            return ChatResponse.textOnly("Sorry, an error occurred while processing your request: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildGeminiRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();

        // System Instruction with UI Context
        String systemPrompt = buildSystemPrompt(request.context());
        body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        ));

        // Conversation History and Current Message
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.history() != null) {
            for (Map<String, String> item : request.history()) {
                String role = "assistant".equalsIgnoreCase(item.get("role")) ? "model" : "user";
                contents.add(Map.of(
                        "role", role,
                        "parts", List.of(Map.of("text", item.getOrDefault("content", "")))
                ));
            }
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.message()))
        ));
        body.put("contents", contents);

        // Native Tools Declaration
        body.put("tools", toolRegistry.getGeminiToolsDeclaration());

        return body;
    }

    private String buildSystemPrompt(UiContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an intelligent, proactive email assistant embedded inside Nebula Mail.\n");
        sb.append("You control and interact with the user's email client directly using native function calling tools.\n");
        sb.append("RULES:\n");
        sb.append("1. NEVER use regex or guess commands; use your registered function calling tools.\n");
        sb.append("2. When the user asks to filter, search, open an email, navigate folders, or compose/reply, ALWAYS invoke the corresponding tool.\n");
        sb.append("3. For context-aware instructions (e.g., 'reply to this', 'summarize this'), inspect the current email context provided below.\n");
        sb.append("4. Keep your text responses friendly, professional, concise, and informative.\n\n");

        sb.append("--- CURRENT UI CONTEXT ---\n");
        if (context != null) {
            sb.append("Active Folder: ").append(context.currentFolder() != null ? context.currentFolder() : "INBOX").append("\n");
            sb.append("Compose Window Open: ").append(context.isComposeOpen()).append("\n");

            if (context.selectedEmailId() != null && !context.selectedEmailId().isBlank()) {
                sb.append("Currently Opened Email:\n");
                sb.append("  - ID: ").append(context.selectedEmailId()).append("\n");
                sb.append("  - Subject: ").append(context.selectedEmailSubject()).append("\n");
                sb.append("  - From: ").append(context.selectedEmailFrom()).append("\n");
                if (context.selectedEmailSnippet() != null) {
                    sb.append("  - Snippet: ").append(context.selectedEmailSnippet()).append("\n");
                }
                if (context.selectedEmailBody() != null && !context.selectedEmailBody().isBlank()) {
                    sb.append("  - Body content: ").append(context.selectedEmailBody()).append("\n");
                }
            } else {
                sb.append("Currently Opened Email: None (Viewing list)\n");
            }

            if (context.visibleEmails() != null && !context.visibleEmails().isEmpty()) {
                sb.append("Emails currently visible in the list:\n");
                for (Map<String, Object> email : context.visibleEmails()) {
                    sb.append("  - [").append(email.get("id")).append("] From: ")
                            .append(email.get("from")).append(" | Subject: ")
                            .append(email.get("subject")).append("\n");
                }
            }
        } else {
            sb.append("Active Folder: INBOX\nCurrently Opened Email: None\n");
        }

        return sb.toString();
    }

    private ChatResponse parseGeminiResponse(String responseJson, UiContext context) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                return ChatResponse.textOnly("I didn't receive any response from the AI model.");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");

            StringBuilder textReply = new StringBuilder();
            List<UiAction> uiActions = new ArrayList<>();

            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    // Extract text part if present
                    if (part.has("text")) {
                        textReply.append(part.path("text").asText()).append(" ");
                    }

                    // Extract function call if present
                    if (part.has("functionCall")) {
                        JsonNode functionCall = part.path("functionCall");
                        String functionName = functionCall.path("name").asText();
                        JsonNode argsNode = functionCall.path("args");
                        Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);

                        UiAction action = mapFunctionCallToUiAction(functionName, args, context);
                        if (action != null) {
                            uiActions.add(action);
                        }
                    }
                }
            }

            String finalReply = textReply.toString().trim();

            // If a tool was invoked without accompanying text, provide a clear friendly confirmation
            if (finalReply.isBlank() && !uiActions.isEmpty()) {
                finalReply = generateActionConfirmation(uiActions.get(0));
            } else if (finalReply.isBlank()) {
                finalReply = "How can I assist you with your emails today?";
            }

            return new ChatResponse(finalReply, uiActions, null);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage(), e);
            return ChatResponse.textOnly("Received response, but had trouble parsing it: " + e.getMessage());
        }
    }

    private UiAction mapFunctionCallToUiAction(String functionName, Map<String, Object> args, UiContext context) {
        log.info("Gemini invoked function: {} with args: {}", functionName, args);

        switch (functionName) {
            case "navigate_folder":
                String folder = (String) args.getOrDefault("folder", "INBOX");
                return new UiAction("NAVIGATE_FOLDER", Map.of("folder", folder.toUpperCase()));

            case "search_emails":
                return new UiAction("FILTER_EMAILS", args != null ? args : Map.of());

            case "open_email":
                String emailId = (String) args.get("emailId");
                if (emailId == null && context != null && context.visibleEmails() != null) {
                    // Match by keyword if emailId wasn't direct
                    String keyword = (String) args.get("subjectKeyword");
                    if (keyword != null) {
                        for (Map<String, Object> email : context.visibleEmails()) {
                            String subj = (String) email.get("subject");
                            if (subj != null && subj.toLowerCase().contains(keyword.toLowerCase())) {
                                emailId = (String) email.get("id");
                                break;
                            }
                        }
                    }
                }
                return new UiAction("OPEN_EMAIL", Map.of("emailId", emailId != null ? emailId : ""));

            case "compose_email":
                return new UiAction("POPULATE_COMPOSE", args != null ? args : Map.of());

            case "reply_to_email":
                Map<String, Object> replyParams = new HashMap<>();
                replyParams.put("replyBody", args.getOrDefault("replyBody", ""));
                if (context != null) {
                    replyParams.put("emailId", context.selectedEmailId());
                    replyParams.put("to", context.selectedEmailFrom());
                    String subj = context.selectedEmailSubject();
                    if (subj != null && !subj.toLowerCase().startsWith("re:")) {
                        subj = "Re: " + subj;
                    }
                    replyParams.put("subject", subj != null ? subj : "");
                }
                return new UiAction("POPULATE_REPLY", replyParams);

            case "summarize_email":
                return new UiAction("SUMMARIZE_EMAIL", args != null ? args : Map.of());

            case "mark_as_read":
                return new UiAction("MARK_READ", args != null ? args : Map.of());

            default:
                log.warn("Unknown function call from Gemini: {}", functionName);
                return null;
        }
    }

    private String generateActionConfirmation(UiAction action) {
        switch (action.action()) {
            case "NAVIGATE_FOLDER":
                return "Navigated to your " + action.parameters().get("folder") + " folder.";
            case "FILTER_EMAILS":
                return "Filtered your mailbox based on your criteria.";
            case "OPEN_EMAIL":
                return "Opened the requested email.";
            case "POPULATE_COMPOSE":
                return "I've drafted the email for you in the compose window. Please review and click Send.";
            case "POPULATE_REPLY":
                return "I've drafted a reply for you in the composer. Please review and click Send.";
            case "MARK_READ":
                return "Updated the read status of the email.";
            default:
                return "Action executed successfully.";
        }
    }
}
