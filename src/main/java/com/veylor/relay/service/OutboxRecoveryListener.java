package com.veylor.relay.service;

import com.veylor.relay.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecoveryListener {

    private final NotificationJobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverProcessingJobs() {
        log.info("Application is ready. Recovering any stuck PROCESSING outbox jobs back to PENDING...");
        try {
            jobRepository.resetProcessingJobsToPending();
            log.info("Outbox recovery complete.");
        } catch (Exception e) {
            log.error("Failed to recover stuck PROCESSING outbox jobs", e);
        }
    }
}
