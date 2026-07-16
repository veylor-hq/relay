package com.veylor.relay.repository;

import com.veylor.relay.entity.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, UUID> {
}
