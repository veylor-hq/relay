package com.veylor.relay.repository;

import com.veylor.relay.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByAccessKeyHash(String accessKeyHash);

    @Query("SELECT a FROM Application a LEFT JOIN FETCH a.authorizedSenders WHERE a.id = :id")
    Optional<Application> findByIdWithSenders(@Param("id") UUID id);
}
