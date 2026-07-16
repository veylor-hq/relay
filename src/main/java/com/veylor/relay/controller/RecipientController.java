package com.veylor.relay.controller;

import com.veylor.relay.dto.RecipientSyncRequest;
import com.veylor.relay.dto.RecipientSyncResponse;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.RecipientRepository;
import com.veylor.relay.util.EmailSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientRepository recipientRepository;

    @PostMapping("/sync")
    public ResponseEntity<RecipientSyncResponse> syncRecipient(@Valid @RequestBody RecipientSyncRequest request) {
        String sanitizedEmail = EmailSanitizer.sanitize(request.getEmail());

        Optional<Recipient> existing = recipientRepository.findBySanitizedEmail(sanitizedEmail);

        Recipient recipient;
        String status;
        if (existing.isPresent()) {
            recipient = existing.get();
            status = "EXISTING";
        } else {
            recipient = Recipient.builder()
                    .sanitizedEmail(sanitizedEmail)
                    .build();
            recipient = recipientRepository.save(recipient);
            status = "PROVISIONED";
        }

        RecipientSyncResponse response = RecipientSyncResponse.builder()
                .recipientId(recipient.getId())
                .sanitizedEmail(recipient.getSanitizedEmail())
                .status(status)
                .build();

        return ResponseEntity.ok(response);
    }
}
