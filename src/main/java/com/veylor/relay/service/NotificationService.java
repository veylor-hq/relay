package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.util.EmailSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final RecipientService recipientService;
    private final NotificationLogRepository notificationLogRepository;
    private final PlatformTransactionManager transactionManager;
    private final LogStatus logStatus;
    private final ObjectProvider<NotificationService> selfProvider;

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
                    selfProvider.getIfAvailable().processSingleItemForBulk(item, application, batchId);
                });
            } catch (Exception e) {
                log.error("Failed to send/log notification for recipientId {}: {}",
                         item.getRecipientId() != null ? item.getRecipientId() : "[email-based]", e.getMessage(), e);
            }
        }
        log.info("Finished processing of bulk notification batch {}", batchId);
    }

    @Transactional
    public void processSingleItemForBulk(NotificationItem item, Application application, UUID batchId) {
        log.info("processSingleItemForBulk transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());
        Recipient recipient = resolveRecipient(item);

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

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boolean emailSentSuccessfully = false;

                    try {
                        sendEmail(recipient.getSanitizedEmail(), item.getSubject(), item.getContent());
                        emailSentSuccessfully = true;
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);

                        try {
                            logStatus.updateLogStatus(saved.getId(), "FAILED");
                        } catch (Exception dbEx) {
                            log.error("Failed to update status to FAILED for savedId {}", saved.getId(), dbEx);
                        }
                    }

                    if (emailSentSuccessfully) {
                        try {
                            logStatus.updateLogStatus(saved.getId(), "SENT");
                        } catch (Exception e) {
                            log.error("Email sent successfully, but failed to update log status to SENT for savedId {}: {}",
                                    saved.getId(), e.getMessage(), e);
                        }
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
        log.info("processSingleNotification transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());
        Recipient recipient = resolveRecipient(item);

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
                        logStatus.updateLogStatus(saved.getId(), "SENT");
                    } catch (Exception e) {
                        log.error("Failed to send email for recipientId {}: {}", recipient.getId(), e.getMessage(), e);
                        logStatus.updateLogStatus(saved.getId(), "FAILED");
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

    public record NotificationResult(UUID logId, String resolvedEmail, String status) {

    }

    private Recipient resolveRecipient(NotificationItem item) {
        if (item.getRecipientId() != null) {
            return recipientService.findById(item.getRecipientId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient not found for ID: " + item.getRecipientId()));
        } else if (item.getEmail() != null) {
            String sanitizedEmail = EmailSanitizer.sanitize(item.getEmail());
            return recipientService.findBySanitizedEmail(sanitizedEmail)
                    .orElseGet(() -> {
                        try {
                            return recipientService.createRecipientWithNewTransaction(sanitizedEmail);
                        } catch (DataIntegrityViolationException e) {
                            return recipientService.findBySanitizedEmail(sanitizedEmail)
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
