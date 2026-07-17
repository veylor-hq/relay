package com.veylor.relay.security;

import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.IdempotentRequest;
import com.veylor.relay.repository.IdempotentRequestRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(2)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotentRequestRepository idempotentRequestRepository;
    private final PlatformTransactionManager transactionManager;

    public IdempotencyFilter(IdempotentRequestRepository idempotentRequestRepository, PlatformTransactionManager transactionManager) {
        this.idempotentRequestRepository = idempotentRequestRepository;
        this.transactionManager = transactionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/notifications")) {
            filterChain.doFilter(request, response);
            return;
        }

        String nonceHeader = request.getHeader("X-RELAY-Nonce");
        Application application = (Application) request.getAttribute("authenticatedApplication");

        if (nonceHeader != null && !nonceHeader.trim().isEmpty() && application != null) {
            try {
                UUID nonce = UUID.fromString(nonceHeader.trim());

                // Check if already completed
                Optional<IdempotentRequest> existingOpt = idempotentRequestRepository.findByApplicationIdAndNonce(application.getId(), nonce);
                if (existingOpt.isPresent()) {
                    IdempotentRequest existing = existingOpt.get();
                    if (existing.getCompleted()) {
                        // Replay the original response
                        response.setStatus(existing.getStatusCode() != null ? existing.getStatusCode() : HttpServletResponse.SC_OK);
                        response.setContentType("application/json");
                        if (existing.getResponseBody() != null) {
                            response.getWriter().write(existing.getResponseBody());
                        } else {
                            response.getWriter().write("{\"status\":\"success\",\"message\":\"Duplicate request processed successfully (idempotent)\"}");
                        }
                        return;
                    }
                    // Already reserved but not completed - reject as conflict
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    response.getWriter().write("Request with this nonce is already in progress.");
                    return;
                } else {
                    // Reserve the nonce
                    IdempotentRequest record = IdempotentRequest.builder()
                            .application(application)
                            .nonce(nonce)
                            .completed(false)
                            .build();
                    idempotentRequestRepository.saveAndFlush(record);
                }
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid UUID format for X-RELAY-Nonce");
                return;
            }

            // Wrap response to capture output
            IdempotencyResponseWrapper responseWrapper = new IdempotencyResponseWrapper(response);

            try {
                filterChain.doFilter(request, responseWrapper);

                // Mark as completed and store response
                UUID nonce = UUID.fromString(nonceHeader.trim());
                markCompleted(application.getId(), nonce, responseWrapper.getStatus(), responseWrapper.getCaptureAsString());
            } catch (Exception e) {
                // Mark as completed with error status
                UUID nonce = UUID.fromString(nonceHeader.trim());
                markCompleted(application.getId(), nonce, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "{\"error\":\"Internal server error\"}");
                throw e;
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    protected void markCompleted(UUID applicationId, UUID nonce, int statusCode, String responseBody) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            idempotentRequestRepository.findByApplicationIdAndNonce(applicationId, nonce).ifPresent(record -> {
                record.setCompleted(true);
                record.setStatusCode(statusCode);
                record.setResponseBody(responseBody);
                idempotentRequestRepository.save(record);
            });
        });
    }
}
