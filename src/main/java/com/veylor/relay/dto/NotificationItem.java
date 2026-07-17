package com.veylor.relay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ExactlyOneRecipientIdentifier
public class NotificationItem {

    @Email(message = "Invalid email format")
    private String email;

    private java.util.UUID recipientId;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Level is required")
    private String level;
}
