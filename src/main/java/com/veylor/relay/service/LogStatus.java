package com.veylor.relay.service;

import com.veylor.relay.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LogStatus {

    private final NotificationLogRepository notificationLogRepository;

    public LogStatus(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLogStatus(UUID logId, String status) {
        notificationLogRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(status);
            notificationLogRepository.save(logEntry);
        });
    }
}