package com.veylor.relay.controller;

import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.repository.EmailSenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminApplicationControllerTest {

    private ApplicationRepository applicationRepository;
    private EmailSenderRepository emailSenderRepository;
    private AdminApplicationController controller;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        emailSenderRepository = mock(EmailSenderRepository.class);
        controller = new AdminApplicationController(applicationRepository, emailSenderRepository);
    }

    @Test
    void testCreateApplication() {
        AdminApplicationController.CreateApplicationRequest req = 
                new AdminApplicationController.CreateApplicationRequest("New App");

        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
            Application app = invocation.getArgument(0);
            app.setId(UUID.randomUUID());
            return app;
        });

        ResponseEntity<AdminApplicationController.CreateApplicationResponse> response = controller.createApplication(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New App", response.getBody().getName());
        assertNotNull(response.getBody().getApiKey());
        assertNotNull(response.getBody().getId());
        assertTrue(response.getBody().getEnabled());
    }

    @Test
    void testGetApplication() {
        UUID id = UUID.randomUUID();
        Application app = Application.builder().id(id).name("App").enabled(true).build();
        when(applicationRepository.findByIdWithSenders(id)).thenReturn(Optional.of(app));

        ResponseEntity<AdminApplicationController.ApplicationResponse> response = controller.getApplication(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("App", response.getBody().getName());
        assertTrue(response.getBody().getEnabled());
    }

    @Test
    void testUpdateApplication() {
        UUID id = UUID.randomUUID();
        Application app = Application.builder().id(id).name("Old App").enabled(true).build();
        when(applicationRepository.findByIdWithSenders(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        AdminApplicationController.UpdateApplicationRequest req = 
                new AdminApplicationController.UpdateApplicationRequest("New App", false);

        ResponseEntity<AdminApplicationController.ApplicationResponse> response = controller.updateApplication(id, req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New App", response.getBody().getName());
        assertFalse(response.getBody().getEnabled());
    }

    @Test
    void testRotateCredentials() {
        UUID id = UUID.randomUUID();
        Application app = Application.builder().id(id).name("Old App").accessKeyHash("oldhash").build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        ResponseEntity<AdminApplicationController.RotateCredentialsResponse> response = controller.rotateCredentials(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getNewApiKey());
        assertTrue(response.getBody().getNewApiKey().startsWith("vlr_"));
        verify(applicationRepository, times(1)).save(app);
    }

    @Test
    void testAssignSender() {
        UUID appId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Application app = Application.builder().id(appId).name("App").authorizedSenders(new HashSet<>()).build();
        EmailSender sender = EmailSender.builder().id(senderId).name("Sender").build();

        when(applicationRepository.findByIdWithSenders(appId)).thenReturn(Optional.of(app));
        when(emailSenderRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<AdminApplicationController.ApplicationResponse> response = controller.assignSender(appId, senderId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getAuthorizedSenderIds().contains(senderId));
    }

    @Test
    void testDeleteApplication() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.existsById(id)).thenReturn(true);

        ResponseEntity<Void> response = controller.deleteApplication(id);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(applicationRepository, times(1)).deleteById(id);
    }
}
