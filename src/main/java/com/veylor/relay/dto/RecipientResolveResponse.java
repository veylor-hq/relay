package com.veylor.relay.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientResolveResponse {
    private UUID recipientId;
    private String email;
    private String name;
    private String metadata;
}
