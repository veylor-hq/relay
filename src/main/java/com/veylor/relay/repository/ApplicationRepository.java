package com.veylor.relay.repository;

import com.veylor.relay.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByAccessKeyHash(String accessKeyHash);
}
