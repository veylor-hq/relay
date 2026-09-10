package com.veylor.relay.controller;

import com.veylor.relay.dto.RecipientResolveRequest;
import com.veylor.relay.dto.RecipientResolveResponse;
import com.veylor.relay.dto.RecipientSyncRequest;
import com.veylor.relay.dto.RecipientSyncResponse;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.RecipientRepository;
import com.veylor.relay.util.EmailSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientRepository recipientRepository;

    @PostMapping("/sync")
    public ResponseEntity<RecipientSyncResponse> syncRecipient(
            @Valid @RequestBody RecipientSyncRequest request,
            HttpServletRequest httpServletRequest) {

        Application app = (Application) httpServletRequest.getAttribute("authenticatedApplication");
        String sanitizedEmail = EmailSanitizer.sanitize(request.getEmail());

        Optional<Recipient> existing = recipientRepository.findBySanitizedEmail(sanitizedEmail);

        Recipient recipient;
        String status;
        if (existing.isPresent()) {
            recipient = existing.get();
            // Update name or metadata if provided
            boolean changed = false;
            if (request.getName() != null && !request.getName().equals(recipient.getName())) {
                recipient.setName(request.getName());
                changed = true;
            }
            if (request.getMetadata() != null && !request.getMetadata().equals(recipient.getMetadata())) {
                recipient.setMetadata(request.getMetadata());
                changed = true;
            }
            if (changed) {
                recipient = recipientRepository.save(recipient);
            }
            status = "EXISTING";
        } else {
            try {
                recipient = Recipient.builder()
                        .sanitizedEmail(sanitizedEmail)
                        .name(request.getName())
                        .metadata(request.getMetadata())
                        .createdByApp(app)
                        .build();
                recipient = recipientRepository.save(recipient);
                status = "PROVISIONED";
            } catch (DataIntegrityViolationException e) {
                // Race condition - another thread created this recipient
                recipient = recipientRepository.findBySanitizedEmail(sanitizedEmail)
                        .orElseThrow(() -> new IllegalStateException("Recipient not found after concurrent creation"));
                status = "EXISTING";
            }
        }

        RecipientSyncResponse response = RecipientSyncResponse.builder()
                .recipientId(recipient.getId())
                .sanitizedEmail(recipient.getSanitizedEmail())
                .name(recipient.getName())
                .status(status)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/resolve")
    public ResponseEntity<RecipientResolveResponse> resolveRecipient(
            @Valid @RequestBody RecipientResolveRequest request,
            HttpServletRequest httpServletRequest) {

        Application app = (Application) httpServletRequest.getAttribute("authenticatedApplication");
        if (app == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Recipient> recipientOpt = recipientRepository.findById(request.getRecipientId());
        if (recipientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Recipient recipient = recipientOpt.get();

        // Access control:
        // An application can resolve recipients it created, or legacy recipients where createdByApp is null.
        if (recipient.getCreatedByApp() != null && !recipient.getCreatedByApp().getId().equals(app.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        RecipientResolveResponse response = RecipientResolveResponse.builder()
                .recipientId(recipient.getId())
                .email(recipient.getSanitizedEmail())
                .name(recipient.getName())
                .metadata(recipient.getMetadata())
                .build();

        return ResponseEntity.ok(response);
    }
}
