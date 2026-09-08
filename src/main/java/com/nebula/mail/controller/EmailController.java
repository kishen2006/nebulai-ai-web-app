package com.nebula.mail.controller;

import com.google.api.client.auth.oauth2.Credential;
import com.nebula.mail.model.EmailDetail;
import com.nebula.mail.model.EmailListResponse;
import com.nebula.mail.model.ReplyEmailRequest;
import com.nebula.mail.model.SearchFilter;
import com.nebula.mail.model.SendEmailRequest;
import com.nebula.mail.model.UserProfile;
import com.nebula.mail.service.GmailService;
import com.nebula.mail.service.GoogleAuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST Controller exposing endpoints for real Gmail operations.
 */
@RestController
@RequestMapping("/api/emails")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);

    private final GmailService gmailService;
    private final GoogleAuthService authService;

    public EmailController(GmailService gmailService, GoogleAuthService authService) {
        this.gmailService = gmailService;
        this.authService = authService;
    }

    /**
     * Lists emails with optional search filters and pagination.
     */
    @GetMapping
    public ResponseEntity<EmailListResponse> listEmails(
            @RequestParam(value = "folder", defaultValue = "INBOX") String folder,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "sender", required = false) String sender,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(value = "pageToken", required = false) String pageToken,
            @RequestParam(value = "maxResults", defaultValue = "20") int maxResults,
            HttpSession session
    ) {
        Credential credential = getRequiredCredential(session);
        SearchFilter filter = new SearchFilter(folder, keyword, sender, dateFrom, dateTo, unreadOnly);

        try {
            EmailListResponse response = gmailService.listEmails(credential, filter, pageToken, maxResults);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Failed to list emails: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch emails: " + ex.getMessage());
        }
    }

    /**
     * Fetches full email message details by message ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailDetail> getEmailDetail(@PathVariable("id") String id, HttpSession session) {
        Credential credential = getRequiredCredential(session);

        try {
            EmailDetail detail = gmailService.getEmailDetail(credential, id);
            return ResponseEntity.ok(detail);
        } catch (Exception ex) {
            log.error("Failed to retrieve email detail for {}: {}", id, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load email: " + ex.getMessage());
        }
    }

    /**
     * Sends a new email message via Gmail.
     */
    @PostMapping("/send")
    public ResponseEntity<EmailDetail> sendEmail(
            @Valid @RequestBody SendEmailRequest request,
            HttpSession session
    ) {
        Credential credential = getRequiredCredential(session);
        UserProfile userProfile = authService.getUserProfile(session);
        String senderEmail = (userProfile != null && userProfile.email() != null) ? userProfile.email() : "me";

        try {
            EmailDetail sentEmail = gmailService.sendEmail(credential, senderEmail, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(sentEmail);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", request.to(), ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email: " + ex.getMessage());
        }
    }

    /**
     * Replies to an existing email thread.
     */
    @PostMapping("/reply")
    public ResponseEntity<EmailDetail> replyEmail(
            @Valid @RequestBody ReplyEmailRequest request,
            HttpSession session
    ) {
        Credential credential = getRequiredCredential(session);
        UserProfile userProfile = authService.getUserProfile(session);
        String senderEmail = (userProfile != null && userProfile.email() != null) ? userProfile.email() : "me";

        try {
            EmailDetail sentReply = gmailService.replyEmail(credential, senderEmail, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(sentReply);
        } catch (Exception ex) {
            log.error("Failed to reply to email {}: {}", request.emailId(), ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reply to email: " + ex.getMessage());
        }
    }

    /**
     * Marks an email as read or unread.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable("id") String id,
            @RequestParam(value = "read", defaultValue = "true") boolean isRead,
            HttpSession session
    ) {
        Credential credential = getRequiredCredential(session);

        try {
            gmailService.markAsRead(credential, id, isRead);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "isRead", isRead
            ));
        } catch (Exception ex) {
            log.error("Failed to mark email {} as read={}: {}", id, isRead, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update read state: " + ex.getMessage());
        }
    }

    private Credential getRequiredCredential(HttpSession session) {
        Credential credential = authService.getCredential(session);
        if (credential == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not logged in with Google.");
        }
        return credential;
    }
}
