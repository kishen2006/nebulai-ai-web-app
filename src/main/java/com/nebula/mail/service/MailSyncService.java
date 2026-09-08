package com.nebula.mail.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Profile;
import com.nebula.mail.model.UserProfile;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing real-time mail synchronization via Gmail history tracking
 * and Server-Sent Events (SSE) broadcasting to active browser sessions.
 */
@Service
public class MailSyncService {

    private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

    private final GmailService gmailService;
    private final GoogleAuthService authService;

    // Active SSE emitters keyed by user email
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // Latest known Gmail historyId keyed by user email
    private final Map<String, BigInteger> lastKnownHistoryIds = new ConcurrentHashMap<>();

    public MailSyncService(GmailService gmailService, GoogleAuthService authService) {
        this.gmailService = gmailService;
        this.authService = authService;
    }

    /**
     * Subscribes a browser client to the user's real-time mail SSE event stream.
     */
    public SseEmitter subscribe(HttpSession session) {
        UserProfile profile = authService.getUserProfile(session);
        if (profile == null || profile.email() == null) {
            throw new IllegalStateException("User not authenticated.");
        }

        String email = profile.email();
        // 30-minute timeout for SSE emitter
        SseEmitter emitter = new SseEmitter(1800_000L);

        userEmitters.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Store initial historyId if available
        if (profile.historyId() != null && !profile.historyId().isBlank()) {
            lastKnownHistoryIds.putIfAbsent(email, new BigInteger(profile.historyId()));
        }

        emitter.onCompletion(() -> removeEmitter(email, emitter));
        emitter.onTimeout(() -> removeEmitter(email, emitter));
        emitter.onError(e -> removeEmitter(email, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "email", email,
                            "historyId", profile.historyId() != null ? profile.historyId() : ""
                    )));
        } catch (IOException e) {
            removeEmitter(email, emitter);
        }

        return emitter;
    }

    /**
     * Periodically polls Gmail for mailbox updates across active users every 20 seconds.
     */
    @Scheduled(fixedDelay = 20000, initialDelay = 10000)
    public void pollMailboxUpdates() {
        if (userEmitters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<SseEmitter>> entry : userEmitters.entrySet()) {
            String email = entry.getKey();
            List<SseEmitter> emitters = entry.getValue();

            if (emitters.isEmpty()) {
                continue;
            }

            Credential credential = authService.getActiveCredentialByEmail(email);
            if (credential == null) {
                continue;
            }

            try {
                checkForUpdates(email, credential, emitters);
            } catch (Exception ex) {
                log.debug("Sync check failed for {}: {}", email, ex.getMessage());
            }
        }
    }

    /**
     * Manually triggers a sync check for a user session.
     */
    public boolean triggerSync(HttpSession session) {
        UserProfile profile = authService.getUserProfile(session);
        if (profile == null || profile.email() == null) {
            return false;
        }

        Credential credential = authService.getCredential(session);
        if (credential == null) {
            return false;
        }

        List<SseEmitter> emitters = userEmitters.get(profile.email());
        try {
            return checkForUpdates(profile.email(), credential, emitters != null ? emitters : List.of());
        } catch (Exception e) {
            log.error("Manual sync failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkForUpdates(String email, Credential credential, List<SseEmitter> emitters) throws IOException {
        Gmail gmail = gmailService.getGmailClient(credential);
        Profile currentProfile = gmail.users().getProfile("me").execute();

        BigInteger currentHistoryId = currentProfile.getHistoryId();
        BigInteger lastHistoryId = lastKnownHistoryIds.get(email);

        if (lastHistoryId == null) {
            lastKnownHistoryIds.put(email, currentHistoryId);
            return false;
        }

        // If historyId hasn't advanced, no changes occurred
        if (currentHistoryId.compareTo(lastHistoryId) <= 0) {
            return false;
        }

        log.info("Mailbox update detected for {}! historyId advanced from {} to {}", email, lastHistoryId, currentHistoryId);

        boolean hasNewMessages = false;
        try {
            ListHistoryResponse historyResponse = gmail.users().history().list("me")
                    .setStartHistoryId(lastHistoryId)
                    .execute();

            List<History> historyRecords = historyResponse.getHistory();
            if (historyRecords != null && !historyRecords.isEmpty()) {
                for (History h : historyRecords) {
                    if (h.getMessagesAdded() != null && !h.getMessagesAdded().isEmpty()) {
                        hasNewMessages = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("History list error for {}: {}", email, e.getMessage());
            hasNewMessages = true; // Fallback to notify
        }

        // Update stored historyId
        lastKnownHistoryIds.put(email, currentHistoryId);

        // Broadcast sync event to client
        broadcast(emitters, "SYNC", Map.of(
                "hasNewMessages", hasNewMessages,
                "historyId", currentHistoryId.toString(),
                "messagesTotal", currentProfile.getMessagesTotal() != null ? currentProfile.getMessagesTotal() : 0L
        ));

        return true;
    }

    private void broadcast(List<SseEmitter> emitters, String eventName, Object data) {
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        List<SseEmitter> list = userEmitters.get(email);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                userEmitters.remove(email);
            }
        }
    }
}
