package com.veylor.relay.controller;

import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.repository.RecipientRepository;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final RecipientRepository recipientRepository;
    private final NotificationLogRepository notificationLogRepository;

    @GetMapping("/recipients")
    public ResponseEntity<Page<AdminRecipientResponse>> searchRecipients(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Recipient> results;
        if (query != null && !query.trim().isEmpty()) {
            results = recipientRepository.searchRecipients(query.trim(), pageRequest);
        } else {
            results = recipientRepository.findAll(pageRequest);
        }

        Page<AdminRecipientResponse> mapped = results.map(r -> AdminRecipientResponse.builder()
                .id(r.getId())
                .sanitizedEmail(r.getSanitizedEmail())
                .name(r.getName())
                .metadata(r.getMetadata())
                .createdByAppId(r.getCreatedByApp() != null ? r.getCreatedByApp().getId() : null)
                .createdAt(r.getCreatedAt())
                .build());

        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<AdminNotificationLogResponse>> listNotificationLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationLog> logs;
        if (status != null && !status.isBlank()) {
            logs = notificationLogRepository.findByStatus(status.toUpperCase(), pageRequest);
        } else {
            logs = notificationLogRepository.findAll(pageRequest);
        }

        Page<AdminNotificationLogResponse> mapped = logs.map(l -> AdminNotificationLogResponse.builder()
                .id(l.getId())
                .batchId(l.getBatchId())
                .applicationId(l.getApplication() != null ? l.getApplication().getId() : null)
                .applicationName(l.getApplication() != null ? l.getApplication().getName() : null)
                .recipientId(l.getRecipient() != null ? l.getRecipient().getId() : null)
                .senderId(l.getSender() != null ? l.getSender().getId() : null)
                .type(l.getType())
                .level(l.getLevel())
                .status(l.getStatus())
                .errorDetails(l.getErrorDetails())
                .createdAt(l.getCreatedAt())
                .processedAt(l.getProcessedAt())
                .build());

        return ResponseEntity.ok(mapped);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminRecipientResponse {
        private UUID id;
        private String sanitizedEmail;
        private String name;
        private String metadata;
        private UUID createdByAppId;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminNotificationLogResponse {
        private UUID id;
        private UUID batchId;
        private UUID applicationId;
        private String applicationName;
        private UUID recipientId;
        private UUID senderId;
        private String type;
        private String level;
        private String status;
        private String errorDetails;
        private Instant createdAt;
        private Instant processedAt;
    }
}
