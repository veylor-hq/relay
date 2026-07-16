package com.veylor.relay.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkNotificationRequest {

    @NotEmpty(message = "Notifications list cannot be empty")
    @Valid
    private List<NotificationItem> notifications;
}
