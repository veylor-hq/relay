package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.NotificationJob;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.repository.NotificationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private RecipientService recipientService;
    private NotificationLogRepository notificationLogRepository;
    private NotificationJobRepository notificationJobRepository;
    private ObjectProvider<NotificationService> selfProvider;
    private NotificationService notificationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        recipientService = mock(RecipientService.class);
        notificationLogRepository = mock(NotificationLogRepository.class);
        notificationJobRepository = mock(NotificationJobRepository.class);
        org.springframework.transaction.PlatformTransactionManager transactionManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        selfProvider = mock(ObjectProvider.class);

        notificationService = new NotificationService(
                recipientService,
                notificationLogRepository,
                notificationJobRepository,
                transactionManager,
                selfProvider
        );

        when(selfProvider.getIfAvailable()).thenReturn(notificationService);
    }

    @Test
    void testProcessBulkNotifications() {
        NotificationItem item = NotificationItem.builder()
                .email("test.user+tag@gmail.com")
                .subject("Hey")
                .content("Hello World")
                .type("EMAIL")
                .level("INFO")
                .build();

        Application app = Application.builder().id(UUID.randomUUID()).name("Client").build();
        UUID batchId = UUID.randomUUID();

        Recipient mockRecipient = Recipient.builder()
                .id(UUID.randomUUID())
                .sanitizedEmail("testuser@gmail.com")
                .build();

        when(recipientService.findBySanitizedEmail("testuser@gmail.com"))
                .thenReturn(Optional.of(mockRecipient));

        NotificationLog mockLog = NotificationLog.builder().id(UUID.randomUUID()).build();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(mockLog);

        notificationService.processBulkNotifications(List.of(item), app, batchId);

        // Verify audit log is saved with hashed details
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());
        assertEquals("EMAIL", logCaptor.getValue().getType());
        assertEquals("INFO", logCaptor.getValue().getLevel());
        assertNotNull(logCaptor.getValue().getSubject());
        assertNotEquals("Hey", logCaptor.getValue().getSubject()); // Subject is hashed

        // Verify transient job is saved with raw details
        ArgumentCaptor<NotificationJob> jobCaptor = ArgumentCaptor.forClass(NotificationJob.class);
        verify(notificationJobRepository, times(1)).save(jobCaptor.capture());
        assertEquals("EMAIL", jobCaptor.getValue().getType());
        assertEquals("INFO", jobCaptor.getValue().getLevel());
        assertEquals("Hey", jobCaptor.getValue().getSubject()); // Subject is plain text
        assertEquals("Hello World", jobCaptor.getValue().getContent()); // Content is plain text
    }

    @Test
    void testProcessSingleNotification() {
        NotificationItem item = NotificationItem.builder()
                .email("single.user+tag@gmail.com")
                .subject("Transactional Alert")
                .content("Immediate password reset link...")
                .type("EMAIL")
                .level("WARN")
                .build();

        Application app = Application.builder().id(UUID.randomUUID()).name("Client").build();
        Recipient mockRecipient = Recipient.builder()
                .id(UUID.randomUUID())
                .sanitizedEmail("singleuser@gmail.com")
                .build();

        when(recipientService.findBySanitizedEmail("singleuser@gmail.com"))
                .thenReturn(Optional.of(mockRecipient));

        NotificationLog mockLog = NotificationLog.builder().id(UUID.randomUUID()).build();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(mockLog);

        NotificationService.NotificationResult result = notificationService.processSingleNotification(item, app);

        assertNotNull(result);
        assertEquals(mockLog.getId(), result.logId());
        assertEquals("PENDING", result.status());

        // Verify audit log is saved with hashed details
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());
        assertNotEquals("Transactional Alert", logCaptor.getValue().getSubject());

        // Verify transient job is saved with raw details
        ArgumentCaptor<NotificationJob> jobCaptor = ArgumentCaptor.forClass(NotificationJob.class);
        verify(notificationJobRepository, times(1)).save(jobCaptor.capture());
        assertEquals("Transactional Alert", jobCaptor.getValue().getSubject());
        assertEquals("Immediate password reset link...", jobCaptor.getValue().getContent());
    }

    @Test
    void testResolveRecipientFallbacksOnDataIntegrityViolation() {
        NotificationItem item = NotificationItem.builder()
                .email("conflict@gmail.com")
                .subject("Transactional Alert")
                .content("...")
                .type("EMAIL")
                .level("INFO")
                .build();

        Application app = Application.builder().id(UUID.randomUUID()).name("Client").build();
        Recipient mockRecipient = Recipient.builder()
                .id(UUID.randomUUID())
                .sanitizedEmail("conflict@gmail.com")
                .build();

        when(recipientService.findBySanitizedEmail("conflict@gmail.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockRecipient));

        when(recipientService.createRecipientWithNewTransaction("conflict@gmail.com"))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));

        NotificationLog mockLog = NotificationLog.builder().id(UUID.randomUUID()).build();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(mockLog);

        NotificationService.NotificationResult result = notificationService.processSingleNotification(item, app);

        assertNotNull(result);
        assertEquals("conflict@gmail.com", result.resolvedEmail());
        verify(recipientService, times(2)).findBySanitizedEmail("conflict@gmail.com");
        verify(recipientService, times(1)).createRecipientWithNewTransaction("conflict@gmail.com");
    }
}
