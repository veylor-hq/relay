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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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
    public void processBulkNotifications(List<NotificationItem> items, Application application, UUID batchId) {
        log.info("Starting processing of bulk notification batch {} with {} items on thread {}",
                 batchId, items.size(), Thread.currentThread());

        for (NotificationItem item : items) {
            try {
                processSingleItemForBulk(item, application, batchId);
            } catch (Exception e) {
                log.error("Failed to send/log notification for recipientId {}: {}",
                         item.getRecipientId() != null ? item.getRecipientId() : "[email-based]", e.getMessage(), e);
            }
        }
        log.info("Finished processing of bulk notification batch {}", batchId);
    }

    @Transactional
    protected void processSingleItemForBulk(NotificationItem item, Application application, UUID batchId) {
        Recipient recipient = resolveRecipient(item);

        // Persist pending log entry first
        NotificationLog logEntry = NotificationLog.builder()
                .batchId(batchId)
                .application(application)
                .recipient(recipient)
                .type(item.getType())
                .level(item.getLevel())
                .subject(hashString(item.getSubject()))
                .content(hashString(item.getContent()))
                .build();
        notificationLogRepository.save(logEntry);

        // Send email after transaction commits
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);
                    }
                }
            });
        } else {
            sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());
        }
    }

    @Transactional
    public NotificationResult processSingleNotification(NotificationItem item, Application application) {
        Recipient recipient = resolveRecipient(item);

        // Persist pending log entry first
        NotificationLog logEntry = NotificationLog.builder()
                .application(application)
                .recipient(recipient)
                .type(item.getType())
                .level(item.getLevel())
                .subject(hashString(item.getSubject()))
                .content(hashString(item.getContent()))
                .build();

        NotificationLog saved = notificationLogRepository.save(logEntry);

        // Send email after transaction commits
        final String recipientEmail = recipient.getSanitizedEmail();
        final String subject = item.getSubject();
        final String content = item.getContent();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sendEmail(recipientEmail, subject, content);
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);
                    }
                }
            });
        } else {
            sendEmail(recipientEmail, subject, content);
        }

        return new NotificationResult(saved.getId(), recipientEmail);
    }

    public static class NotificationResult {
        private final UUID logId;
        private final String resolvedEmail;

        public NotificationResult(UUID logId, String resolvedEmail) {
            this.logId = logId;
            this.resolvedEmail = resolvedEmail;
        }

        public UUID getLogId() {
            return logId;
        }

        public String getResolvedEmail() {
            return resolvedEmail;
        }
    }

    private Recipient resolveRecipient(NotificationItem item) {
        if (item.getRecipientId() != null) {
            return recipientRepository.findById(item.getRecipientId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient not found for ID: " + item.getRecipientId()));
        } else if (item.getEmail() != null) {
            String sanitizedEmail = EmailSanitizer.sanitize(item.getEmail());
            return recipientRepository.findBySanitizedEmail(sanitizedEmail)
                    .orElseGet(() -> {
                        try {
                            Recipient newRecipient = Recipient.builder()
                                    .sanitizedEmail(sanitizedEmail)
                                    .build();
                            return recipientRepository.save(newRecipient);
                        } catch (DataIntegrityViolationException e) {
                            // Race condition - another thread created this recipient
                            return recipientRepository.findBySanitizedEmail(sanitizedEmail)
                                    .orElseThrow(() -> new IllegalStateException("Recipient not found after concurrent creation"));
                        }
                    });
        } else {
            throw new IllegalArgumentException("Either email or recipientId must be provided");
        }
    }

    private String hashString(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "redacted";
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
