package com.veylor.relay.controller;

import com.veylor.relay.entity.Application;
import com.veylor.relay.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminApplicationControllerTest {

    private ApplicationRepository applicationRepository;
    private AdminApplicationController controller;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        controller = new AdminApplicationController(applicationRepository);
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
    }

    @Test
    void testGetApplication() {
        UUID id = UUID.randomUUID();
        Application app = Application.builder().id(id).name("App").build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        ResponseEntity<AdminApplicationController.ApplicationResponse> response = controller.getApplication(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("App", response.getBody().getName());
    }

    @Test
    void testUpdateApplication() {
        UUID id = UUID.randomUUID();
        Application app = Application.builder().id(id).name("Old App").build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        AdminApplicationController.UpdateApplicationRequest req = 
                new AdminApplicationController.UpdateApplicationRequest("New App");

        ResponseEntity<AdminApplicationController.ApplicationResponse> response = controller.updateApplication(id, req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("New App", response.getBody().getName());
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
