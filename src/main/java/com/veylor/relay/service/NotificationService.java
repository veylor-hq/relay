package com.veylor.relay.service;

import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.entity.NotificationJob;
import com.veylor.relay.entity.NotificationLog;
import com.veylor.relay.entity.Recipient;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.repository.NotificationJobRepository;
import com.veylor.relay.repository.NotificationLogRepository;
import com.veylor.relay.util.EmailSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RecipientService recipientService;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final EmailSenderRepository emailSenderRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<NotificationService> selfProvider;

    public void processBulkNotifications(List<NotificationItem> items, Application application, UUID batchId) {
        log.info("Starting processing of bulk notification batch {} with {} items on thread {}",
                 batchId, items.size(), Thread.currentThread());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (NotificationItem item : items) {
            try {
                Recipient recipient = resolveRecipient(item, application);
                EmailSender sender = resolveSender(item.getSenderId(), application);
                transactionTemplate.executeWithoutResult(status -> {
                    selfProvider.getIfAvailable().saveLogAndJobForBulk(item, application, recipient, sender, batchId);
                });
            } catch (Exception e) {
                log.error("Failed to enqueue notification for recipientId {}: {}",
                         item.getRecipientId() != null ? item.getRecipientId() : "[email-based]", e.getMessage(), e);
            }
        }
        log.info("Finished processing of bulk notification batch {}", batchId);
    }

    @Transactional
    public void saveLogAndJobForBulk(NotificationItem item, Application application, Recipient recipient, EmailSender sender, UUID batchId) {
        log.info("saveLogAndJobForBulk transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());

        NotificationLog logEntry = NotificationLog.builder()
                .batchId(batchId)
                .application(application)
                .recipient(recipient)
                .sender(sender)
                .type(item.getType() != null ? item.getType() : "EMAIL")
                .level(item.getLevel() != null ? item.getLevel() : "INFO")
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
                .sender(sender)
                .type(item.getType() != null ? item.getType() : "EMAIL")
                .level(item.getLevel() != null ? item.getLevel() : "INFO")
                .subject(item.getSubject())
                .content(item.getContent())
                .status("PENDING")
                .build();
        notificationJobRepository.save(jobEntry);
    }

    public NotificationResult processSingleNotification(NotificationItem item, Application application) {
        Recipient recipient = resolveRecipient(item, application);
        EmailSender sender = resolveSender(item.getSenderId(), application);
        return selfProvider.getIfAvailable().saveLogAndJob(item, application, recipient, sender);
    }

    @Transactional
    public NotificationResult saveLogAndJob(NotificationItem item, Application application, Recipient recipient, EmailSender sender) {
        log.info("saveLogAndJob transaction active: {}", TransactionSynchronizationManager.isActualTransactionActive());

        NotificationLog logEntry = NotificationLog.builder()
                .application(application)
                .recipient(recipient)
                .sender(sender)
                .type(item.getType() != null ? item.getType() : "EMAIL")
                .level(item.getLevel() != null ? item.getLevel() : "INFO")
                .subject(hashString(item.getSubject()))
                .content(hashString(item.getContent()))
                .status("PENDING")
                .build();
        NotificationLog saved = notificationLogRepository.save(logEntry);

        NotificationJob jobEntry = NotificationJob.builder()
                .id(saved.getId())
                .application(application)
                .recipient(recipient)
                .sender(sender)
                .type(item.getType() != null ? item.getType() : "EMAIL")
                .level(item.getLevel() != null ? item.getLevel() : "INFO")
                .subject(item.getSubject())
                .content(item.getContent())
                .status("PENDING")
                .build();
        notificationJobRepository.save(jobEntry);

        final String recipientEmail = recipient.getSanitizedEmail();
        String outcomeStatus = "PENDING";

        return new NotificationResult(saved.getId(), recipientEmail, outcomeStatus);
    }

    public EmailSender resolveSender(UUID requestedSenderId, Application application) {
        List<EmailSender> authorizedSenders = emailSenderRepository.findEnabledSendersByApplicationId(application.getId());

        if (requestedSenderId != null) {
            return authorizedSenders.stream()
                    .filter(s -> s.getId().equals(requestedSenderId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Application is not authorized to use Email Sender: " + requestedSenderId));
        }

        // If no senderId explicitly specified:
        if (authorizedSenders.size() == 1) {
            return authorizedSenders.get(0);
        } else if (authorizedSenders.isEmpty()) {
            // Fall back to null (system default mail sender)
            return null;
        } else {
            // Multiple senders assigned; caller should explicitly choose
            return authorizedSenders.get(0);
        }
    }

    public record NotificationResult(UUID logId, String resolvedEmail, String status) {

    }

    private Recipient resolveRecipient(NotificationItem item, Application application) {
        if (item.getRecipientId() != null) {
            return recipientService.findById(item.getRecipientId())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient not found for ID: " + item.getRecipientId()));
        } else if (item.getEmail() != null) {
            String sanitizedEmail = EmailSanitizer.sanitize(item.getEmail());
            return recipientService.findBySanitizedEmail(sanitizedEmail)
                    .orElseGet(() -> {
                        try {
                            return recipientService.createRecipientWithNewTransaction(sanitizedEmail, null, null, application);
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
