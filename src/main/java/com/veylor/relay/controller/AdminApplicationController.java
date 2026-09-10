package com.veylor.relay.controller;

import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.util.HmacUtils;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationRepository applicationRepository;
    private final EmailSenderRepository emailSenderRepository;

    @PostMapping
    public ResponseEntity<CreateApplicationResponse> createApplication(@RequestBody CreateApplicationRequest request) {
        String rawApiKey = "vlr_" + UUID.randomUUID().toString().replace("-", "");
        String accessKeyHash = HmacUtils.sha256Hex(rawApiKey);

        Application application = Application.builder()
                .name(request.getName())
                .accessKeyHash(accessKeyHash)
                .enabled(true)
                .build();

        if (request.getSenderId() != null) {
            emailSenderRepository.findById(request.getSenderId()).ifPresent(s -> {
                application.getAuthorizedSenders().add(s);
            });
        }

        Application saved = applicationRepository.save(application);

        CreateApplicationResponse response = CreateApplicationResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .apiKey(rawApiKey)
                .enabled(saved.getEnabled())
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        List<ApplicationResponse> applications = applicationRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        return applicationRepository.findByIdWithSenders(id)
                .map(this::mapToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApplicationResponse> updateApplication(@PathVariable UUID id, @RequestBody UpdateApplicationRequest request) {
        return applicationRepository.findByIdWithSenders(id)
                .map(app -> {
                    if (request.getName() != null && !request.getName().isBlank()) {
                        app.setName(request.getName());
                    }
                    if (request.getEnabled() != null) {
                        app.setEnabled(request.getEnabled());
                    }
                    Application saved = applicationRepository.save(app);
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rotate-credentials")
    @Transactional
    public ResponseEntity<RotateCredentialsResponse> rotateCredentials(@PathVariable UUID id) {
        return applicationRepository.findById(id)
                .map(app -> {
                    String rawApiKey = "vlr_" + UUID.randomUUID().toString().replace("-", "");
                    String accessKeyHash = HmacUtils.sha256Hex(rawApiKey);
                    app.setAccessKeyHash(accessKeyHash);
                    applicationRepository.save(app);

                    return ResponseEntity.ok(RotateCredentialsResponse.builder()
                            .id(app.getId())
                            .name(app.getName())
                            .newApiKey(rawApiKey)
                            .message("Credentials rotated successfully. Store this new API key securely.")
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public ResponseEntity<ApplicationResponse> disableApplication(@PathVariable UUID id) {
        return applicationRepository.findByIdWithSenders(id)
                .map(app -> {
                    app.setEnabled(false);
                    Application saved = applicationRepository.save(app);
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enable")
    @Transactional
    public ResponseEntity<ApplicationResponse> enableApplication(@PathVariable UUID id) {
        return applicationRepository.findByIdWithSenders(id)
                .map(app -> {
                    app.setEnabled(true);
                    Application saved = applicationRepository.save(app);
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/senders/{senderId}")
    @Transactional
    public ResponseEntity<ApplicationResponse> assignSender(@PathVariable UUID id, @PathVariable UUID senderId) {
        var appOpt = applicationRepository.findByIdWithSenders(id);
        var senderOpt = emailSenderRepository.findById(senderId);

        if (appOpt.isEmpty() || senderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Application app = appOpt.get();
        EmailSender sender = senderOpt.get();

        app.getAuthorizedSenders().add(sender);
        Application saved = applicationRepository.save(app);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @DeleteMapping("/{id}/senders/{senderId}")
    @Transactional
    public ResponseEntity<ApplicationResponse> unassignSender(@PathVariable UUID id, @PathVariable UUID senderId) {
        var appOpt = applicationRepository.findByIdWithSenders(id);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Application app = appOpt.get();
        app.getAuthorizedSenders().removeIf(s -> s.getId().equals(senderId));
        Application saved = applicationRepository.save(app);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PutMapping("/{id}/sender")
    @Transactional
    public ResponseEntity<ApplicationResponse> setApplicationSender(
            @PathVariable UUID id,
            @RequestBody SetSenderRequest request) {

        var appOpt = applicationRepository.findByIdWithSenders(id);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Application app = appOpt.get();
        app.getAuthorizedSenders().clear();

        if (request.getSenderId() != null) {
            var senderOpt = emailSenderRepository.findById(request.getSenderId());
            if (senderOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            app.getAuthorizedSenders().add(senderOpt.get());
        }

        Application saved = applicationRepository.save(app);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable UUID id) {
        if (applicationRepository.existsById(id)) {
            applicationRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private ApplicationResponse mapToResponse(Application app) {
        Set<UUID> senderIds = app.getAuthorizedSenders() != null
                ? app.getAuthorizedSenders().stream().map(EmailSender::getId).collect(Collectors.toSet())
                : Set.of();

        UUID primarySenderId = null;
        String primarySenderName = null;
        String primarySenderFromAddress = null;

        if (app.getAuthorizedSenders() != null && !app.getAuthorizedSenders().isEmpty()) {
            EmailSender primary = app.getAuthorizedSenders().iterator().next();
            primarySenderId = primary.getId();
            primarySenderName = primary.getName();
            primarySenderFromAddress = primary.getFromAddress();
        }

        return ApplicationResponse.builder()
                .id(app.getId())
                .name(app.getName())
                .enabled(app.getEnabled() != null ? app.getEnabled() : true)
                .authorizedSenderIds(senderIds)
                .primarySenderId(primarySenderId)
                .primarySenderName(primarySenderName)
                .primarySenderFromAddress(primarySenderFromAddress)
                .createdAt(app.getCreatedAt())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateApplicationRequest {
        private String name;
        private UUID senderId;

        public CreateApplicationRequest(String name) {
            this.name = name;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetSenderRequest {
        private UUID senderId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateApplicationResponse {
        private UUID id;
        private String name;
        private String apiKey;
        private Boolean enabled;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateApplicationRequest {
        private String name;
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RotateCredentialsResponse {
        private UUID id;
        private String name;
        private String newApiKey;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApplicationResponse {
        private UUID id;
        private String name;
        private Boolean enabled;
        private Set<UUID> authorizedSenderIds;
        private UUID primarySenderId;
        private String primarySenderName;
        private String primarySenderFromAddress;
        private Instant createdAt;
    }
}
