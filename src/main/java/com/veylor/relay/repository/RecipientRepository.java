package com.veylor.relay.repository;

import com.veylor.relay.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {
    Optional<Recipient> findBySanitizedEmail(String email);
}
