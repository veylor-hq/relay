package com.veylor.relay.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStatusResponse {
    private UUID id;
    private UUID batchId;
    private UUID applicationId;
    private UUID recipientId;
    private UUID senderId;
    private String type;
    private String level;
    private String status;
    private String errorDetails;
    private Instant createdAt;
    private Instant processedAt;
}
