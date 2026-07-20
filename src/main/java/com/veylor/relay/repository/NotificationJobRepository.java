package com.veylor.relay.repository;

import com.veylor.relay.entity.NotificationJob;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // '-2' tells Hibernate/PostgreSQL to use "SKIP LOCKED"
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT j FROM NotificationJob j JOIN FETCH j.recipient JOIN FETCH j.application WHERE j.status = 'PENDING' AND (j.retryAfter IS NULL OR j.retryAfter <= CURRENT_TIMESTAMP) ORDER BY j.createdAt ASC")
    List<NotificationJob> findNextPendingJobs(Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE NotificationJob j SET j.status = 'PENDING' WHERE j.status = 'PROCESSING'")
    void resetProcessingJobsToPending();
}
