package com.veylor.relay.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientSyncResponse {
    private UUID recipientId;
    private String sanitizedEmail;
    private String status;
}
