package com.veylor.relay.controller;

import com.veylor.relay.dto.RecipientSyncRequest;
import com.veylor.relay.dto.RecipientSyncResponse;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipientControllerTest {

    private RecipientRepository recipientRepository;
    private RecipientController controller;

    @BeforeEach
    void setUp() {
        recipientRepository = mock(RecipientRepository.class);
        controller = new RecipientController(recipientRepository);
    }

    @Test
    void testSyncRecipientNewProvisioned() {
        RecipientSyncRequest request = new RecipientSyncRequest("John.Doe+promo@gmail.com");

        when(recipientRepository.findBySanitizedEmail("johndoe@gmail.com"))
                .thenReturn(Optional.empty());

        when(recipientRepository.save(any(Recipient.class))).thenAnswer(invocation -> {
            Recipient r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ResponseEntity<RecipientSyncResponse> response = controller.syncRecipient(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("johndoe@gmail.com", response.getBody().getSanitizedEmail());
        assertEquals("PROVISIONED", response.getBody().getStatus());
        assertNotNull(response.getBody().getRecipientId());
    }

    @Test
    void testSyncRecipientExisting() {
        RecipientSyncRequest request = new RecipientSyncRequest("John.Doe@gmail.com");
        UUID id = UUID.randomUUID();
        Recipient existingRecipient = Recipient.builder()
                .id(id)
                .sanitizedEmail("johndoe@gmail.com")
                .build();

        when(recipientRepository.findBySanitizedEmail("johndoe@gmail.com"))
                .thenReturn(Optional.of(existingRecipient));

        ResponseEntity<RecipientSyncResponse> response = controller.syncRecipient(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("johndoe@gmail.com", response.getBody().getSanitizedEmail());
        assertEquals("EXISTING", response.getBody().getStatus());
        assertEquals(id, response.getBody().getRecipientId());
        verify(recipientRepository, never()).save(any());
    }
}
