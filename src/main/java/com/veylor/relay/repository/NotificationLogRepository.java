package com.veylor.relay.repository;

import com.veylor.relay.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Optional<NotificationLog> findByIdAndApplicationId(UUID id, UUID applicationId);

    List<NotificationLog> findByBatchIdAndApplicationId(UUID batchId, UUID applicationId);

    Page<NotificationLog> findByApplicationId(UUID applicationId, Pageable pageable);

    Page<NotificationLog> findByStatus(String status, Pageable pageable);
}
