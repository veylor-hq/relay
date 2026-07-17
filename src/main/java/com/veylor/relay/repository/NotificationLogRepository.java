package com.veylor.relay.repository;

import com.veylor.relay.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
