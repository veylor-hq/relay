package com.veylor.relay.repository;

import com.veylor.relay.entity.Recipient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RecipientRepositoryTest {

    @Autowired
    private RecipientRepository recipientRepository;

    @Test
    void testPrePersistSanitizesEmail() {
        Recipient recipient = Recipient.builder()
                .sanitizedEmail("John.Doe+extra@gmail.com")
                .build();

        Recipient saved = recipientRepository.saveAndFlush(recipient);

        assertEquals("johndoe@gmail.com", saved.getSanitizedEmail());
    }

    @Test
    void testPreUpdateSanitizesEmail() {
        Recipient recipient = Recipient.builder()
                .sanitizedEmail("original@example.com")
                .build();

        Recipient saved = recipientRepository.saveAndFlush(recipient);
        assertEquals("original@example.com", saved.getSanitizedEmail());

        saved.setSanitizedEmail("New.User+test@gmail.com");
        Recipient updated = recipientRepository.saveAndFlush(saved);

        assertEquals("newuser@gmail.com", updated.getSanitizedEmail());
    }
}
