package com.veylor.relay.controller;

import com.veylor.relay.dto.BulkNotificationRequest;
import com.veylor.relay.dto.BulkResponse;
import com.veylor.relay.dto.NotificationItem;
import com.veylor.relay.dto.SingleResponse;
import com.veylor.relay.entity.Application;
import com.veylor.relay.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/bulk")
    public ResponseEntity<BulkResponse> sendBulkNotifications(
            @Valid @RequestBody BulkNotificationRequest request,
            HttpServletRequest httpServletRequest) {

        Application application = (Application) httpServletRequest.getAttribute("authenticatedApplication");
        if (application == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID batchId = UUID.randomUUID();

        notificationService.processBulkNotifications(request.getNotifications(), application, batchId);

        BulkResponse response = BulkResponse.builder()
                .batchId(batchId)
                .status("ACCEPTED")
                .message("Bulk notifications batch accepted for processing")
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/single")
    public ResponseEntity<SingleResponse> sendSingleNotification(
            @Valid @RequestBody NotificationItem request,
            HttpServletRequest httpServletRequest) {

        Application application = (Application) httpServletRequest.getAttribute("authenticatedApplication");
        if (application == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NotificationService.NotificationResult result = notificationService.processSingleNotification(request, application);

        String responseEmail = (request.getEmail() != null) ? result.resolvedEmail() : null;
        SingleResponse response = SingleResponse.builder()
                .logId(result.logId())
                .status(result.status())
                .recipientEmail(responseEmail)
                .build();

        return ResponseEntity.ok(response);
    }
}
