package com.veylor.relay.service;

import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientService {

    private final RecipientRepository recipientRepository;

    @Transactional(readOnly = true)
    public Optional<Recipient> findById(UUID id) {
        return recipientRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Recipient> findBySanitizedEmail(String email) {
        return recipientRepository.findBySanitizedEmail(email);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recipient createRecipientWithNewTransaction(String sanitizedEmail, String name, String metadata, Application app) {
        Recipient newRecipient = Recipient.builder()
                .sanitizedEmail(sanitizedEmail)
                .name(name)
                .metadata(metadata)
                .createdByApp(app)
                .build();
        return recipientRepository.saveAndFlush(newRecipient);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recipient createRecipientWithNewTransaction(String sanitizedEmail) {
        return createRecipientWithNewTransaction(sanitizedEmail, null, null, null);
    }
}
