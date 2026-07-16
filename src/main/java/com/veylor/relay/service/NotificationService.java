package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.repository.RecipientRepository;
import com.veylor.relay.util.EmailSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final RecipientRepository recipientRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${spring.mail.from-address}")
    private String fromAddress;

    @Async("taskExecutor")
    @Transactional
    public void processBulkNotifications(List<NotificationItem> items, Application application, UUID batchId) {
        log.info("Starting processing of bulk notification batch {} with {} items on thread {}", 
                 batchId, items.size(), Thread.currentThread());

        for (NotificationItem item : items) {
            try {
                Recipient recipient = resolveRecipient(item);

                sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());

                NotificationLog logEntry = NotificationLog.builder()
                        .batchId(batchId)
                        .application(application)
                        .recipient(recipient)
                        .type(item.getType())
                        .level(item.getLevel())
                        .subject(item.getSubject())
                        .content(item.getContent())
                        .build();
                notificationLogRepository.save(logEntry);

            } catch (Exception e) {
                log.error("Failed to send/log notification for recipient {}: {}", item.getEmail(), e.getMessage(), e);
            }
        }
        log.info("Finished processing of bulk notification batch {}", batchId);
    }

    @Transactional
    public UUID processSingleNotification(NotificationItem item, Application application) {
        Recipient recipient = resolveRecipient(item);

        sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());

        NotificationLog logEntry = NotificationLog.builder()
                .application(application)
                .recipient(recipient)
                .type(item.getType())
                .level(item.getLevel())
                .subject(item.getSubject())
                .content(item.getContent())
                .build();

        NotificationLog saved = notificationLogRepository.save(logEntry);
        return saved.getId();
    }

    private Recipient resolveRecipient(NotificationItem item) {
        if (item.getRecipientId() != null) {
            return recipientRepository.findById(item.getRecipientId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient not found for ID: " + item.getRecipientId()));
        } else if (item.getEmail() != null) {
            String sanitizedEmail = EmailSanitizer.sanitize(item.getEmail());
            return recipientRepository.findBySanitizedEmail(sanitizedEmail)
                    .orElseGet(() -> {
                        Recipient newRecipient = Recipient.builder()
                                .sanitizedEmail(sanitizedEmail)
                                .build();
                        return recipientRepository.save(newRecipient);
                    });
        } else {
            throw new IllegalArgumentException("Either email or recipientId must be provided");
        }
    }

    private void sendEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        message.setFrom(fromAddress);
        mailSender.send(message);
    }
}
