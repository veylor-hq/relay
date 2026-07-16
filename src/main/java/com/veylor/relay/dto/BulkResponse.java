package com.veylor.relay.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkResponse {
    private UUID batchId;
    private String status;
    private String message;
}
