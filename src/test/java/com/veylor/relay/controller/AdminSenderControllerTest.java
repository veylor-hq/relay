package com.veylor.relay.controller;

import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.service.EmailSenderProviderService;
import com.veylor.relay.util.EncryptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminSenderControllerTest {

    private EmailSenderRepository emailSenderRepository;
    private EmailSenderProviderService emailSenderProviderService;
    private AdminSenderController controller;
    private final String masterKey = "test-master-key-32-chars-long!!";

    @BeforeEach
    void setUp() {
        emailSenderRepository = mock(EmailSenderRepository.class);
        emailSenderProviderService = mock(EmailSenderProviderService.class);
        controller = new AdminSenderController(emailSenderRepository, emailSenderProviderService);
        ReflectionTestUtils.setField(controller, "masterKey", masterKey);
    }

    @Test
    void testCreateSenderEncryptsPassword() {
        AdminSenderController.CreateSenderRequest request = new AdminSenderController.CreateSenderRequest();
        request.setName("eGarage Sender");
        request.setFromAddress("reports@egarageapp.uk");
        request.setSmtpHost("smtp.egarageapp.uk");
        request.setSmtpPort(587);
        request.setUsername("reports");
        request.setPassword("plainPassword123");

        when(emailSenderRepository.save(any(EmailSender.class))).thenAnswer(invocation -> {
            EmailSender s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ResponseEntity<AdminSenderController.SenderResponse> response = controller.createSender(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("eGarage Sender", response.getBody().getName());
        assertEquals("reports@egarageapp.uk", response.getBody().getFromAddress());
        assertTrue(response.getBody().isHasPassword());

        // Verify that plainPassword123 was NOT saved plaintext
        verify(emailSenderRepository).save(argThat(sender ->
                sender.getEncryptedPassword() != null &&
                !sender.getEncryptedPassword().equals("plainPassword123") &&
                EncryptionUtils.decrypt(sender.getEncryptedPassword(), masterKey).equals("plainPassword123")
        ));
    }

    @Test
    void testGetSenderDoesNotExposePassword() {
        UUID id = UUID.randomUUID();
        EmailSender sender = EmailSender.builder()
                .id(id)
                .name("Veylor")
                .fromAddress("no-reply@veylor.dev")
                .smtpHost("smtp.veylor.dev")
                .smtpPort(587)
                .encryptedPassword(EncryptionUtils.encrypt("secret", masterKey))
                .enabled(true)
                .build();

        when(emailSenderRepository.findById(id)).thenReturn(Optional.of(sender));

        ResponseEntity<AdminSenderController.SenderResponse> response = controller.getSender(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isHasPassword());
        // Response object does not even have a getPassword() field
    }

    @Test
    void testDisableSender() {
        UUID id = UUID.randomUUID();
        EmailSender sender = EmailSender.builder()
                .id(id)
                .name("Veylor")
                .enabled(true)
                .build();

        when(emailSenderRepository.findById(id)).thenReturn(Optional.of(sender));
        when(emailSenderRepository.save(any(EmailSender.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<AdminSenderController.SenderResponse> response = controller.disableSender(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getEnabled());
        verify(emailSenderProviderService).evictSenderCache(id);
    }
}
