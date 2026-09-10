package com.veylor.relay.controller;

import com.veylor.relay.dto.RecipientResolveRequest;
import com.veylor.relay.dto.RecipientResolveResponse;
import com.veylor.relay.dto.RecipientSyncRequest;
import com.veylor.relay.dto.RecipientSyncResponse;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

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
        RecipientSyncRequest request = new RecipientSyncRequest("John.Doe+promo@gmail.com", "John Doe", "{\"tier\":\"gold\"}");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        Application app = Application.builder().id(UUID.randomUUID()).name("ClientApp").build();
        servletRequest.setAttribute("authenticatedApplication", app);

        when(recipientRepository.findBySanitizedEmail("johndoe@gmail.com"))
                .thenReturn(Optional.empty());

        when(recipientRepository.save(any(Recipient.class))).thenAnswer(invocation -> {
            Recipient r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ResponseEntity<RecipientSyncResponse> response = controller.syncRecipient(request, servletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("johndoe@gmail.com", response.getBody().getSanitizedEmail());
        assertEquals("John Doe", response.getBody().getName());
        assertEquals("PROVISIONED", response.getBody().getStatus());
        assertNotNull(response.getBody().getRecipientId());
    }

    @Test
    void testSyncRecipientExisting() {
        RecipientSyncRequest request = new RecipientSyncRequest("John.Doe@gmail.com", "John", null);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        UUID id = UUID.randomUUID();
        Recipient existingRecipient = Recipient.builder()
                .id(id)
                .sanitizedEmail("johndoe@gmail.com")
                .name("John")
                .build();

        when(recipientRepository.findBySanitizedEmail("johndoe@gmail.com"))
                .thenReturn(Optional.of(existingRecipient));

        ResponseEntity<RecipientSyncResponse> response = controller.syncRecipient(request, servletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("johndoe@gmail.com", response.getBody().getSanitizedEmail());
        assertEquals("EXISTING", response.getBody().getStatus());
        assertEquals(id, response.getBody().getRecipientId());
    }

    @Test
    void testResolveRecipientAuthorized() {
        UUID recId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        Application app = Application.builder().id(appId).name("App").build();

        Recipient recipient = Recipient.builder()
                .id(recId)
                .sanitizedEmail("test@example.com")
                .name("Test User")
                .metadata("meta")
                .createdByApp(app)
                .build();

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute("authenticatedApplication", app);

        when(recipientRepository.findById(recId)).thenReturn(Optional.of(recipient));

        ResponseEntity<RecipientResolveResponse> response = controller.resolveRecipient(
                new RecipientResolveRequest(recId), servletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("test@example.com", response.getBody().getEmail());
        assertEquals("Test User", response.getBody().getName());
    }

    @Test
    void testResolveRecipientForbiddenForOtherApp() {
        UUID recId = UUID.randomUUID();
        UUID app1Id = UUID.randomUUID();
        UUID app2Id = UUID.randomUUID();
        Application ownerApp = Application.builder().id(app1Id).name("OwnerApp").build();
        Application callerApp = Application.builder().id(app2Id).name("CallerApp").build();

        Recipient recipient = Recipient.builder()
                .id(recId)
                .sanitizedEmail("secret@example.com")
                .createdByApp(ownerApp)
                .build();

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute("authenticatedApplication", callerApp);

        when(recipientRepository.findById(recId)).thenReturn(Optional.of(recipient));

        ResponseEntity<RecipientResolveResponse> response = controller.resolveRecipient(
                new RecipientResolveRequest(recId), servletRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
