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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    @Value("${spring.mail.from-address}")
    private String fromAddress;

    @Async("taskExecutor")
    public void processBulkNotifications(List<NotificationItem> items, Application application, UUID batchId) {
        log.info("Starting processing of bulk notification batch {} with {} items on thread {}",
                 batchId, items.size(), Thread.currentThread());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (NotificationItem item : items) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    processSingleItemForBulk(item, application, batchId);
                });
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
                .status("PENDING")
                .build();
        NotificationLog saved = notificationLogRepository.save(logEntry);

        // Send email after transaction commits
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());
                        updateLogStatus(saved.getId(), "SENT");
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);
                        updateLogStatus(saved.getId(), "FAILED");
                    }
                }
            });
        } else {
            try {
                sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());
                saved.setStatus("SENT");
                notificationLogRepository.save(saved);
            } catch (Exception e) {
                saved.setStatus("FAILED");
                notificationLogRepository.save(saved);
                throw e;
            }
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
                .status("PENDING")
                .build();

        NotificationLog saved = notificationLogRepository.save(logEntry);

        // Send email after transaction commits
        final String recipientEmail = recipient.getSanitizedEmail();
        final String subject = item.getSubject();
        final String content = item.getContent();
        String outcomeStatus = "PENDING";

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        sendEmail(recipientEmail, subject, content);
                        updateLogStatus(saved.getId(), "SENT");
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);
                        updateLogStatus(saved.getId(), "FAILED");
                    }
                }
            });
        } else {
            try {
                sendEmail(recipientEmail, subject, content);
                saved.setStatus("SENT");
                notificationLogRepository.save(saved);
                outcomeStatus = "SENT";
            } catch (Exception e) {
                saved.setStatus("FAILED");
                notificationLogRepository.save(saved);
                throw e;
            }
        }

        return new NotificationResult(saved.getId(), recipientEmail, outcomeStatus);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLogStatus(UUID logId, String status) {
        notificationLogRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(status);
            notificationLogRepository.save(logEntry);
        });
    }

    public static class NotificationResult {
        private final UUID logId;
        private final String resolvedEmail;
        private final String status;

        public NotificationResult(UUID logId, String resolvedEmail, String status) {
            this.logId = logId;
            this.resolvedEmail = resolvedEmail;
            this.status = status;
        }

        public UUID getLogId() {
            return logId;
        }

        public String getResolvedEmail() {
            return resolvedEmail;
        }

        public String getStatus() {
            return status;
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
                if (hex.length() == 1) {
                    hexString.append('0');
                }
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
