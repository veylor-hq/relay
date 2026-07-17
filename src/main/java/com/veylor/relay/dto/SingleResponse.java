package com.veylor.relay.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SingleResponse {
    private UUID logId;
    private String status;
    private String recipientEmail;
}
