package com.veylor.relay.repository;

import com.veylor.relay.entity.EmailSender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailSenderRepository extends JpaRepository<EmailSender, UUID> {

    @Query("SELECT s FROM EmailSender s JOIN s.authorizedApplications a WHERE a.id = :applicationId AND s.enabled = true")
    List<EmailSender> findEnabledSendersByApplicationId(@Param("applicationId") UUID applicationId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM EmailSender s JOIN s.authorizedApplications a WHERE s.id = :senderId AND a.id = :applicationId AND s.enabled = true")
    boolean isSenderAuthorizedForApplication(@Param("senderId") UUID senderId, @Param("applicationId") UUID applicationId);
}
