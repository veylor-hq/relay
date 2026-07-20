package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.entity.NotificationJob;
import com.veylor.relay.repository.NotificationJobRepository;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.util.EmailSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
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

    private final RecipientService recipientService;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<NotificationService> selfProvider;

    public void processBulkNotifications(List<NotificationItem> items, Application application, UUID batchId) {
        log.info("Starting processing of bulk notification batch {} with {} items on thread {}",
                 batchId, items.size(), Thread.currentThread());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (NotificationItem item : items) {
            try {
                Recipient recipient = resolveRecipient(item);
                transactionTemplate.executeWithoutResult(status -> {
                    selfProvider.getIfAvailable().saveLogAndJobForBulk(item, application, recipient, batchId);
                });
            } catch (Exception e) {
                log.error("Failed to send/log notification for recipientId {}: {}",
                         item.getRecipientId() != null ? item.getRecipientId() : "[email-based]", e.getMessage(), e);
            }
        }
        log.info("Finished processing of bulk notification batch {}", batchId);
    }

    @Transactional
    public void saveLogAndJobForBulk(NotificationItem item, Application application, Recipient recipient, UUID batchId) {
        log.info("saveLogAndJobForBulk transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());

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

        NotificationJob jobEntry = NotificationJob.builder()
                .id(saved.getId())
                .batchId(batchId)
                .application(application)
                .recipient(recipient)
                .type(item.getType())
                .level(item.getLevel())
                .subject(item.getSubject())
                .content(item.getContent())
                .status("PENDING")
                .build();
        notificationJobRepository.save(jobEntry);
    }

    public NotificationResult processSingleNotification(NotificationItem item, Application application) {
        Recipient recipient = resolveRecipient(item);
        return selfProvider.getIfAvailable().saveLogAndJob(item, application, recipient);
    }

    @Transactional
    public NotificationResult saveLogAndJob(NotificationItem item, Application application, Recipient recipient) {
        log.info("saveLogAndJob transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());

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

        NotificationJob jobEntry = NotificationJob.builder()
                .id(saved.getId())
                .application(application)
                .recipient(recipient)
                .type(item.getType())
                .level(item.getLevel())
                .subject(item.getSubject())
                .content(item.getContent())
                .status("PENDING")
                .build();
        notificationJobRepository.save(jobEntry);

        final String recipientEmail = recipient.getSanitizedEmail();
        String outcomeStatus = "PENDING";

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


}
