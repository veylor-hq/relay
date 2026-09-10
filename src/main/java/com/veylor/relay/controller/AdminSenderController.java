package com.veylor.relay.controller;

import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.service.EmailSenderProviderService;
import com.veylor.relay.util.EncryptionUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/senders")
@RequiredArgsConstructor
public class AdminSenderController {

    private final EmailSenderRepository emailSenderRepository;
    private final EmailSenderProviderService emailSenderProviderService;

    @Value("${app.security.master-key:veylor-relay-default-master-key-32b}")
    private String masterKey;

    @PostMapping
    public ResponseEntity<SenderResponse> createSender(@Valid @RequestBody CreateSenderRequest request) {
        String encryptedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            encryptedPassword = EncryptionUtils.encrypt(request.getPassword(), masterKey);
        }

        EmailSender sender = EmailSender.builder()
                .name(request.getName())
                .fromAddress(request.getFromAddress())
                .smtpHost(request.getSmtpHost())
                .smtpPort(request.getSmtpPort() > 0 ? request.getSmtpPort() : 587)
                .username(request.getUsername())
                .encryptedPassword(encryptedPassword)
                .authEnabled(request.getAuthEnabled() != null ? request.getAuthEnabled() : true)
                .starttlsEnabled(request.getStarttlsEnabled() != null ? request.getStarttlsEnabled() : true)
                .starttlsRequired(request.getStarttlsRequired() != null ? request.getStarttlsRequired() : true)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        EmailSender saved = emailSenderRepository.save(sender);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<SenderResponse>> listSenders() {
        List<SenderResponse> list = emailSenderRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SenderResponse> getSender(@PathVariable UUID id) {
        return emailSenderRepository.findById(id)
                .map(this::mapToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<SenderResponse> updateSender(@PathVariable UUID id, @RequestBody UpdateSenderRequest request) {
        return emailSenderRepository.findById(id)
                .map(sender -> {
                    if (request.getName() != null) sender.setName(request.getName());
                    if (request.getFromAddress() != null) sender.setFromAddress(request.getFromAddress());
                    if (request.getSmtpHost() != null) sender.setSmtpHost(request.getSmtpHost());
                    if (request.getSmtpPort() != null && request.getSmtpPort() > 0) sender.setSmtpPort(request.getSmtpPort());
                    if (request.getUsername() != null) sender.setUsername(request.getUsername());
                    if (request.getPassword() != null && !request.getPassword().isBlank()) {
                        sender.setEncryptedPassword(EncryptionUtils.encrypt(request.getPassword(), masterKey));
                    }
                    if (request.getAuthEnabled() != null) sender.setAuthEnabled(request.getAuthEnabled());
                    if (request.getStarttlsEnabled() != null) sender.setStarttlsEnabled(request.getStarttlsEnabled());
                    if (request.getStarttlsRequired() != null) sender.setStarttlsRequired(request.getStarttlsRequired());
                    if (request.getEnabled() != null) sender.setEnabled(request.getEnabled());

                    EmailSender saved = emailSenderRepository.save(sender);
                    emailSenderProviderService.evictSenderCache(saved.getId());
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enable")
    @Transactional
    public ResponseEntity<SenderResponse> enableSender(@PathVariable UUID id) {
        return emailSenderRepository.findById(id)
                .map(sender -> {
                    sender.setEnabled(true);
                    EmailSender saved = emailSenderRepository.save(sender);
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public ResponseEntity<SenderResponse> disableSender(@PathVariable UUID id) {
        return emailSenderRepository.findById(id)
                .map(sender -> {
                    sender.setEnabled(false);
                    EmailSender saved = emailSenderRepository.save(sender);
                    emailSenderProviderService.evictSenderCache(saved.getId());
                    return ResponseEntity.ok(mapToResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteSender(@PathVariable UUID id) {
        if (emailSenderRepository.existsById(id)) {
            emailSenderRepository.deleteById(id);
            emailSenderProviderService.evictSenderCache(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private SenderResponse mapToResponse(EmailSender sender) {
        return SenderResponse.builder()
                .id(sender.getId())
                .name(sender.getName())
                .fromAddress(sender.getFromAddress())
                .smtpHost(sender.getSmtpHost())
                .smtpPort(sender.getSmtpPort())
                .username(sender.getUsername())
                .hasPassword(sender.getEncryptedPassword() != null && !sender.getEncryptedPassword().isBlank())
                .authEnabled(sender.getAuthEnabled())
                .starttlsEnabled(sender.getStarttlsEnabled())
                .starttlsRequired(sender.getStarttlsRequired())
                .enabled(sender.getEnabled())
                .createdAt(sender.getCreatedAt())
                .updatedAt(sender.getUpdatedAt())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSenderRequest {
        @NotBlank(message = "Name is required")
        private String name;
        @NotBlank(message = "fromAddress is required")
        private String fromAddress;
        @NotBlank(message = "smtpHost is required")
        private String smtpHost;
        private int smtpPort = 587;
        private String username;
        private String password;
        private Boolean authEnabled = true;
        private Boolean starttlsEnabled = true;
        private Boolean starttlsRequired = true;
        private Boolean enabled = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSenderRequest {
        private String name;
        private String fromAddress;
        private String smtpHost;
        private Integer smtpPort;
        private String username;
        private String password;
        private Boolean authEnabled;
        private Boolean starttlsEnabled;
        private Boolean starttlsRequired;
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SenderResponse {
        private UUID id;
        private String name;
        private String fromAddress;
        private String smtpHost;
        private int smtpPort;
        private String username;
        private boolean hasPassword;
        private Boolean authEnabled;
        private Boolean starttlsEnabled;
        private Boolean starttlsRequired;
        private Boolean enabled;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
