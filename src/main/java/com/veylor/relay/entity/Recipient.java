package com.veylor.relay.entity;

import com.veylor.relay.util.EmailSanitizer;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sanitized_email", nullable = false, unique = true)
    private String sanitizedEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        sanitizeEmail();
    }

    @PreUpdate
    protected void onUpdate() {
        sanitizeEmail();
    }

    private void sanitizeEmail() {
        if (this.sanitizedEmail != null) {
            this.sanitizedEmail = EmailSanitizer.sanitize(this.sanitizedEmail);
        }
    }
}
