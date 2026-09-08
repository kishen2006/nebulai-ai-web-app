package com.nebula.mail.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.nebula.mail.model.EmailDetail;
import com.nebula.mail.model.EmailListResponse;
import com.nebula.mail.model.EmailSummary;
import com.nebula.mail.model.ReplyEmailRequest;
import com.nebula.mail.model.SearchFilter;
import com.nebula.mail.model.SendEmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service encapsulating real Gmail API operations (list, view, send, reply, modify labels).
 */
@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final String APPLICATION_NAME = "Nebula-Mail";

    private final NetHttpTransport netHttpTransport;
    private final JsonFactory jsonFactory;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public GmailService(NetHttpTransport netHttpTransport, JsonFactory jsonFactory) {
        this.netHttpTransport = netHttpTransport;
        this.jsonFactory = jsonFactory;
    }

    /**
     * Builds an authorized Gmail API client for the given user Credential.
     */
    public Gmail getGmailClient(Credential credential) {
        if (credential == null) {
            throw new IllegalArgumentException("User is not authenticated. Missing Google OAuth credential.");
        }
        return new Gmail.Builder(netHttpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Lists emails according to the provided search filter and pagination token.
     */
    public EmailListResponse listEmails(Credential credential, SearchFilter filter, String pageToken, int maxResults) throws IOException {
        Gmail gmail = getGmailClient(credential);
        String query = filter.toGmailQuery();

        log.debug("Querying Gmail messages with query: '{}', pageToken: {}", query, pageToken);

        Gmail.Users.Messages.List listRequest = gmail.users().messages().list("me")
                .setQ(query)
                .setMaxResults((long) Math.min(Math.max(maxResults, 5), 50));

        if (pageToken != null && !pageToken.isBlank()) {
            listRequest.setPageToken(pageToken);
        }

        ListMessagesResponse listResponse = listRequest.execute();
        List<Message> messagePlaceholders = listResponse.getMessages();

        if (messagePlaceholders == null || messagePlaceholders.isEmpty()) {
            return new EmailListResponse(
                    Collections.emptyList(),
                    listResponse.getNextPageToken(),
                    0,
                    0,
                    filter.folder() != null ? filter.folder() : "INBOX"
            );
        }

        // Concurrently fetch metadata for each message in the page for fast response times
        List<CompletableFuture<EmailSummary>> futures = messagePlaceholders.stream()
                .map(msg -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Message fullMsg = gmail.users().messages().get("me", msg.getId())
                                .setFormat("METADATA")
                                .setMetadataHeaders(List.of("Subject", "From", "To", "Date"))
                                .execute();
                        return mapToSummary(fullMsg);
                    } catch (Exception e) {
                        log.warn("Failed to fetch metadata for message {}: {}", msg.getId(), e.getMessage());
                        return null;
                    }
                }, executor))
                .toList();

        List<EmailSummary> summaries = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        // Calculate unread count in current list
        long unreadCount = summaries.stream().filter(EmailSummary::isUnread).count();

        return new EmailListResponse(
                summaries,
                listResponse.getNextPageToken(),
                listResponse.getResultSizeEstimate() != null ? listResponse.getResultSizeEstimate().intValue() : summaries.size(),
                unreadCount,
                filter.folder() != null ? filter.folder() : "INBOX"
        );
    }

    /**
     * Fetches full email message details (including headers, HTML/plain text body).
     */
    public EmailDetail getEmailDetail(Credential credential, String messageId) throws IOException {
        Gmail gmail = getGmailClient(credential);
        Message message = gmail.users().messages().get("me", messageId)
                .setFormat("FULL")
                .execute();

        String subject = getHeader(message, "Subject", "(No Subject)");
        String from = getHeader(message, "From", "Unknown Sender");
        String to = getHeader(message, "To", "");
        String cc = getHeader(message, "Cc", "");
        String date = getHeader(message, "Date", "");

        List<String> labelIds = message.getLabelIds() != null ? message.getLabelIds() : Collections.emptyList();
        boolean isUnread = labelIds.contains("UNREAD");

        StringBuilder bodyHtml = new StringBuilder();
        StringBuilder bodyText = new StringBuilder();
        extractBodyParts(message.getPayload(), bodyText, bodyHtml);

        return new EmailDetail(
                message.getId(),
                message.getThreadId(),
                from,
                to,
                cc,
                subject,
                date,
                message.getInternalDate() != null ? message.getInternalDate() : 0L,
                bodyText.toString().trim(),
                bodyHtml.toString().trim(),
                message.getSnippet(),
                isUnread,
                labelIds
        );
    }

    /**
     * Composes and sends a real email message via Gmail API.
     */
    public EmailDetail sendEmail(Credential credential, String senderEmail, SendEmailRequest request)
            throws IOException, MessagingException {
        Gmail gmail = getGmailClient(credential);

        Properties props = new Properties();
        Session mailSession = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(mailSession);

        mimeMessage.setFrom(new InternetAddress(senderEmail));
        mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(request.to().trim()));

        if (request.cc() != null && !request.cc().isBlank()) {
            mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(request.cc().trim()));
        }

        mimeMessage.setSubject(request.subject(), "UTF-8");

        if (request.isHtml()) {
            mimeMessage.setContent(request.body(), "text/html; charset=utf-8");
        } else {
            mimeMessage.setText(request.body(), "UTF-8");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);

        log.info("Sending real email to {} with subject '{}'...", request.to(), request.subject());
        Message sentMessage = gmail.users().messages().send("me", message).execute();

        return getEmailDetail(credential, sentMessage.getId());
    }

    /**
     * Replies to an existing email thread, preserving In-Reply-To and References headers.
     */
    public EmailDetail replyEmail(Credential credential, String senderEmail, ReplyEmailRequest request)
            throws IOException, MessagingException {
        Gmail gmail = getGmailClient(credential);

        // Fetch original message to extract Message-ID and Subject
        Message originalMessage = gmail.users().messages().get("me", request.emailId())
                .setFormat("METADATA")
                .setMetadataHeaders(List.of("Subject", "Message-ID", "Message-Id"))
                .execute();

        String originalMessageId = getHeader(originalMessage, "Message-ID", "");
        if (originalMessageId.isBlank()) {
            originalMessageId = getHeader(originalMessage, "Message-Id", "");
        }

        String threadId = (request.threadId() != null && !request.threadId().isBlank())
                ? request.threadId()
                : originalMessage.getThreadId();

        Properties props = new Properties();
        Session mailSession = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(mailSession);

        mimeMessage.setFrom(new InternetAddress(senderEmail));
        mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(request.to().trim()));

        String subject = request.subject();
        if (subject == null || subject.isBlank()) {
            String origSubject = getHeader(originalMessage, "Subject", "");
            subject = origSubject.toLowerCase().startsWith("re:") ? origSubject : "Re: " + origSubject;
        }
        mimeMessage.setSubject(subject, "UTF-8");

        if (!originalMessageId.isBlank()) {
            mimeMessage.setHeader("In-Reply-To", originalMessageId);
            mimeMessage.setHeader("References", originalMessageId);
        }

        if (request.isHtml()) {
            mimeMessage.setContent(request.body(), "text/html; charset=utf-8");
        } else {
            mimeMessage.setText(request.body(), "UTF-8");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String encodedEmail = Base64.encodeBase64URLSafeString(buffer.toByteArray());

        Message message = new Message();
        message.setRaw(encodedEmail);
        message.setThreadId(threadId);

        log.info("Sending reply to {} in thread {}...", request.to(), threadId);
        Message sentMessage = gmail.users().messages().send("me", message).execute();

        return getEmailDetail(credential, sentMessage.getId());
    }

    /**
     * Marks an email as read or unread.
     */
    public void markAsRead(Credential credential, String messageId, boolean isRead) throws IOException {
        Gmail gmail = getGmailClient(credential);
        ModifyMessageRequest modifyRequest = new ModifyMessageRequest();

        if (isRead) {
            modifyRequest.setRemoveLabelIds(List.of("UNREAD"));
        } else {
            modifyRequest.setAddLabelIds(List.of("UNREAD"));
        }

        log.info("Marking message {} as read={}", messageId, isRead);
        gmail.users().messages().modify("me", messageId, modifyRequest).execute();
    }

    // --- Helper Methods ---

    private EmailSummary mapToSummary(Message message) {
        String subject = getHeader(message, "Subject", "(No Subject)");
        String from = getHeader(message, "From", "Unknown Sender");
        String to = getHeader(message, "To", "");
        String date = getHeader(message, "Date", "");

        List<String> labelIds = message.getLabelIds() != null ? message.getLabelIds() : Collections.emptyList();
        boolean isUnread = labelIds.contains("UNREAD");

        return new EmailSummary(
                message.getId(),
                message.getThreadId(),
                from,
                to,
                subject,
                message.getSnippet(),
                date,
                message.getInternalDate() != null ? message.getInternalDate() : 0L,
                isUnread,
                labelIds
        );
    }

    private String getHeader(Message message, String headerName, String defaultValue) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return defaultValue;
        }
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (headerName.equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }
        return defaultValue;
    }

    private void extractBodyParts(MessagePart part, StringBuilder text, StringBuilder html) {
        if (part == null) {
            return;
        }

        String mimeType = part.getMimeType();
        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] decoded = Base64.decodeBase64(part.getBody().getData());
            String bodyContent = new String(decoded, StandardCharsets.UTF_8);

            if ("text/plain".equalsIgnoreCase(mimeType)) {
                text.append(bodyContent).append("\n");
            } else if ("text/html".equalsIgnoreCase(mimeType)) {
                html.append(bodyContent).append("\n");
            }
        }

        if (part.getParts() != null) {
            for (MessagePart childPart : part.getParts()) {
                extractBodyParts(childPart, text, html);
            }
        }
    }
}
