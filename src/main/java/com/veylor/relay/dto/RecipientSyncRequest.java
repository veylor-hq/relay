package com.veylor.relay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientSyncRequest {

    @NotBlank(message = "Email is required for synchronization")
    @Email(message = "Invalid email format")
    private String email;
}
