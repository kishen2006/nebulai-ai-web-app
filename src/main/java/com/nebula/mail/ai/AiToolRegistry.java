package com.nebula.mail.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds standard Google Gemini Function Calling declarations for the mail assistant.
 */
@Component
public class AiToolRegistry {

    public List<Map<String, Object>> getGeminiToolsDeclaration() {
        return List.of(Map.of("functionDeclarations", List.of(
                buildSearchEmailsTool(),
                buildNavigateFolderTool(),
                buildOpenEmailTool(),
                buildComposeEmailTool(),
                buildReplyToEmailTool(),
                buildSummarizeEmailTool(),
                buildMarkAsReadTool()
        )));
    }

    private Map<String, Object> buildSearchEmailsTool() {
        return Map.of(
                "name", "search_emails",
                "description", "Searches and filters emails in the current mailbox by keyword, sender, date range, or unread status.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "query", Map.of("type", "STRING", "description", "Keywords or search phrase to match against emails"),
                                "sender", Map.of("type", "STRING", "description", "Sender name or email address (e.g. 'john@example.com' or 'Google')"),
                                "dateFrom", Map.of("type", "STRING", "description", "Start date in YYYY-MM-DD or YYYY/MM/DD format"),
                                "dateTo", Map.of("type", "STRING", "description", "End date in YYYY-MM-DD or YYYY/MM/DD format"),
                                "unreadOnly", Map.of("type", "BOOLEAN", "description", "Set to true to show only unread emails"),
                                "folder", Map.of("type", "STRING", "description", "Mailbox folder to search in ('INBOX' or 'SENT')")
                        )
                )
        );
    }

    private Map<String, Object> buildNavigateFolderTool() {
        return Map.of(
                "name", "navigate_folder",
                "description", "Switches the active mailbox folder view in the UI.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "folder", Map.of("type", "STRING", "description", "Folder to navigate to: 'INBOX' or 'SENT'")
                        ),
                        "required", List.of("folder")
                )
        );
    }

    private Map<String, Object> buildOpenEmailTool() {
        return Map.of(
                "name", "open_email",
                "description", "Opens and displays a specific email message in the detail viewer.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "emailId", Map.of("type", "STRING", "description", "The unique Gmail message ID of the email to open"),
                                "subjectKeyword", Map.of("type", "STRING", "description", "Subject keywords if referring to a visible email by subject")
                        )
                )
        );
    }

    private Map<String, Object> buildComposeEmailTool() {
        return Map.of(
                "name", "compose_email",
                "description", "Opens the email composer in the UI and pre-populates recipient, subject, and drafted body.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "to", Map.of("type", "STRING", "description", "Recipient email address"),
                                "subject", Map.of("type", "STRING", "description", "Subject line of the email"),
                                "body", Map.of("type", "STRING", "description", "Body content of the email drafted by the assistant")
                        ),
                        "required", List.of("to", "subject", "body")
                )
        );
    }

    private Map<String, Object> buildReplyToEmailTool() {
        return Map.of(
                "name", "reply_to_email",
                "description", "Drafts an intelligent context-aware reply to the currently opened email and opens the reply composer.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "replyBody", Map.of("type", "STRING", "description", "The drafted reply message content addressing the sender's message")
                        ),
                        "required", List.of("replyBody")
                )
        );
    }

    private Map<String, Object> buildSummarizeEmailTool() {
        return Map.of(
                "name", "summarize_email",
                "description", "Summarizes the currently viewed email into key highlights, dates, and actionable next steps.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "emailId", Map.of("type", "STRING", "description", "Optional email ID to summarize; defaults to currently opened email")
                        )
                )
        );
    }

    private Map<String, Object> buildMarkAsReadTool() {
        return Map.of(
                "name", "mark_as_read",
                "description", "Marks an email as read or unread in the UI and Gmail.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "emailId", Map.of("type", "STRING", "description", "Email ID to update"),
                                "isRead", Map.of("type", "BOOLEAN", "description", "True to mark read, false to mark unread")
                        ),
                        "required", List.of("emailId", "isRead")
                )
        );
    }
}
