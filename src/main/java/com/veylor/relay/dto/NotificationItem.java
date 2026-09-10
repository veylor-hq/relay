package com.veylor.relay.dto;

import com.veylor.relay.validation.ExactlyOneRecipientIdentifier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ExactlyOneRecipientIdentifier
public class NotificationItem {

    @Email(message = "Invalid email format")
    private String email;

    private UUID recipientId;

    private UUID senderId;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Type is required")
    @Builder.Default
    private String type = "EMAIL";

    @NotBlank(message = "Level is required")
    @Builder.Default
    private String level = "INFO";
}
