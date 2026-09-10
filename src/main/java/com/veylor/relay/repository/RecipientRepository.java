package com.veylor.relay.repository;

import com.veylor.relay.entity.Recipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    Optional<Recipient> findBySanitizedEmail(String email);

    Optional<Recipient> findByIdAndCreatedByAppId(UUID id, UUID createdByAppId);

    @Query("SELECT r FROM Recipient r WHERE LOWER(r.sanitizedEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Recipient> searchRecipients(@Param("query") String query, Pageable pageable);
}
