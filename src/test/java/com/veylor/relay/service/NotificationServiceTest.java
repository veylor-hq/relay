package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private JavaMailSender mailSender;
    private RecipientRepository recipientRepository;
    private NotificationLogRepository notificationLogRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        recipientRepository = mock(RecipientRepository.class);
        notificationLogRepository = mock(NotificationLogRepository.class);
        notificationService = new NotificationService(mailSender, recipientRepository, notificationLogRepository);
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

        when(recipientRepository.findBySanitizedEmail("testuser@gmail.com"))
                .thenReturn(Optional.of(mockRecipient));

        notificationService.processBulkNotifications(List.of(item), app, batchId);

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());
        assertEquals("testuser@gmail.com", mailCaptor.getValue().getTo()[0]);
        assertEquals("Hey", mailCaptor.getValue().getSubject());
        assertEquals("Hello World", mailCaptor.getValue().getText());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());
        assertEquals("EMAIL", logCaptor.getValue().getType());
        assertEquals("INFO", logCaptor.getValue().getLevel());
        assertNotNull(logCaptor.getValue().getSubject());
        assertNotEquals("Hey", logCaptor.getValue().getSubject());
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

        when(recipientRepository.findBySanitizedEmail("singleuser@gmail.com"))
                .thenReturn(Optional.of(mockRecipient));

        NotificationLog mockLog = NotificationLog.builder().id(UUID.randomUUID()).build();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(mockLog);

        NotificationService.NotificationResult result = notificationService.processSingleNotification(item, app);

        assertNotNull(result);
        assertEquals(mockLog.getId(), result.getLogId());

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());
        assertEquals("singleuser@gmail.com", mailCaptor.getValue().getTo()[0]);
        assertEquals("Transactional Alert", mailCaptor.getValue().getSubject());
    }
}
