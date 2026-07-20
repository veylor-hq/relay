package com.veylor.relay.service;

import com.veylor.relay.entity.NotificationJob;
import com.veylor.relay.repository.NotificationJobRepository;
import com.veylor.relay.repository.NotificationLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationJobProcessor {

    private final JavaMailSender mailSender;
    private final NotificationJobRepository jobRepository;
    private final NotificationLogRepository logRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.outbox.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${spring.mail.from-address}")
    private String fromAddress;

    @Value("${app.outbox.rate-limit.emails-per-second:10}")
    private double emailsPerSecond;

    @Value("${app.outbox.retry.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${app.outbox.retry.max-interval-ms:30000}")
    private long maxIntervalMs;

    @Value("${app.outbox.retry.multiplier:2.0}")
    private double retryMultiplier;

    private RateLimiter rateLimiter;

    @PostConstruct
    public void init() {
        this.rateLimiter = new RateLimiter(emailsPerSecond);
    }

    @Async("taskExecutor")
    public void processJobAsync(NotificationJob job) {
        log.info("Processing notification job ID: {} on thread {}", job.getId(), Thread.currentThread());

        int attempt = 0;
        long interval = initialIntervalMs;
        boolean emailSentSuccessfully = false;

        while (attempt < maxRetryAttempts) {
            attempt++;
            try {
                // Apply rate limiting before sending
                rateLimiter.acquire();

                sendEmail(job.getRecipient().getSanitizedEmail(), job.getSubject(), job.getContent());
                emailSentSuccessfully = true;
                break;
            } catch (Exception e) {
                boolean isRateLimited = e.getMessage() != null && (e.getMessage().contains("421") || e.getMessage().toLowerCase().contains("rate limit"));
                long currentBackoff = isRateLimited ? Math.max(interval * 2, 5000) : interval;
                log.warn("Failed to send email for job ID: {} (attempt {}/{}): {}. Backoff: {}ms",
                         job.getId(), attempt, maxRetryAttempts, e.getMessage(), currentBackoff);
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(currentBackoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry backoff sleep interrupted for job ID: {}", job.getId());
                        break;
                    }
                    interval = Math.min((long) (currentBackoff * retryMultiplier), maxIntervalMs);
                }
            }
        }

        final boolean success = emailSentSuccessfully;
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (success) {
                    updateAuditLogStatus(job, "SENT");
                } else {
                    updateAuditLogStatus(job, "FAILED");
                }
                jobRepository.delete(job);
            });
        } catch (Exception e) {
            log.error("Failed to update status and delete Job for job ID {}: {}", job.getId(), e.getMessage(), e);
        }
    }

    private void updateAuditLogStatus(NotificationJob job, String status) {
        logRepository.findById(job.getId()).ifPresent(auditLog -> {
            auditLog.setStatus(status);
            auditLog.setProcessedAt(Instant.now());
            logRepository.save(auditLog);
        });
    }

    private void sendEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        message.setFrom(fromAddress);
        mailSender.send(message);
    }

    private static class RateLimiter {
        private final double permitsPerSecond;
        private double permits;
        private long lastRefillTimestamp;

        public RateLimiter(double permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            this.permits = permitsPerSecond;
            this.lastRefillTimestamp = System.nanoTime();
        }

        public synchronized void acquire() throws InterruptedException {
            while (true) {
                refill();
                if (permits >= 1.0) {
                    permits -= 1.0;
                    return;
                }
                long sleepTimeNanos = (long) ((1.0 - permits) * 1e9 / permitsPerSecond);
                if (sleepTimeNanos > 0) {
                    long sleepMs = sleepTimeNanos / 1_000_000;
                    int sleepNanos = (int) (sleepTimeNanos % 1_000_000);
                    Thread.sleep(sleepMs, sleepNanos);
                }
            }
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTimestamp) / 1e9;
            if (elapsedSeconds > 0) {
                permits = Math.min(permitsPerSecond, permits + elapsedSeconds * permitsPerSecond);
                lastRefillTimestamp = now;
            }
        }
    }
}