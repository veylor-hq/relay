package com.veylor.relay.service;

import com.veylor.relay.entity.NotificationJob;
import com.veylor.relay.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxPoller {

    private final NotificationJobRepository jobRepository;
    private final NotificationJobProcessor jobProcessor;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.outbox.poller.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.processing-timeout-minutes:5}")
    private int processingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${app.outbox.poller.fixed-delay-ms:5000}")
    public void pollAndDispatch() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<NotificationJob> jobsToDispatch = transactionTemplate.execute(status -> {
            List<NotificationJob> pendingJobs = jobRepository.findNextPendingJobs(PageRequest.of(0, batchSize));
            if (pendingJobs.isEmpty()) {
                return List.of();
            }
            Instant now = Instant.now();
            for (NotificationJob job : pendingJobs) {
                job.setStatus("PROCESSING");
                job.setProcessingStartedAt(now);
            }
            return jobRepository.saveAll(pendingJobs);
        });

        if (jobsToDispatch != null && !jobsToDispatch.isEmpty()) {
            log.info("Polled and marked {} jobs as PROCESSING. Dispatching concurrently...", jobsToDispatch.size());
            for (NotificationJob job : jobsToDispatch) {
                jobProcessor.processJobAsync(job);
            }
        }
    }

    /**
     * Periodic sweep to recover any jobs orphaned in PROCESSING status while Relay is running
     * (e.g. if a thread or node suffered an unhandled crash or network interruption).
     */
    @Scheduled(fixedDelayString = "${app.outbox.orphan-sweep-delay-ms:60000}")
    public void recoverOrphanedJobs() {
        try {
            Instant cutoff = Instant.now().minus(processingTimeoutMinutes, ChronoUnit.MINUTES);
            int recovered = jobRepository.recoverOrphanedProcessingJobs(cutoff);
            if (recovered > 0) {
                log.warn("Recovered {} orphaned PROCESSING outbox jobs back to PENDING (exceeded {}m timeout)", 
                         recovered, processingTimeoutMinutes);
            }
        } catch (Exception e) {
            log.error("Failed to recover orphaned processing jobs", e);
        }
    }
}
