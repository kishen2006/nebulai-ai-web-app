package com.nebula.mail.controller;

import com.nebula.mail.service.MailSyncService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Controller exposing real-time SSE stream for mail synchronization.
 */
@RestController
@RequestMapping("/api/emails/sync")
public class SyncController {

    private final MailSyncService syncService;

    public SyncController(MailSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Subscribes browser client to Server-Sent Events stream for real-time inbox changes.
     */
    @GetMapping("/stream")
    public SseEmitter subscribeToSync(HttpSession session) {
        return syncService.subscribe(session);
    }

    /**
     * Manually triggers a sync check against Gmail.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> triggerManualSync(HttpSession session) {
        boolean updated = syncService.triggerSync(session);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "updated", updated
        ));
    }
}
