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

    @Scheduled(fixedDelayString = "${app.outbox.poller.fixed-delay-ms:5000}")
    public void pollAndDispatch() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<NotificationJob> jobsToDispatch = transactionTemplate.execute(status -> {
            List<NotificationJob> pendingJobs = jobRepository.findNextPendingJobs(PageRequest.of(0, batchSize));
            if (pendingJobs.isEmpty()) {
                return List.of();
            }
            for (NotificationJob job : pendingJobs) {
                job.setStatus("PROCESSING");
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
}
