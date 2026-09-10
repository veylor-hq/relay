package com.veylor.relay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientResolveRequest {

    @NotNull(message = "recipientId is required")
    private UUID recipientId;
}
