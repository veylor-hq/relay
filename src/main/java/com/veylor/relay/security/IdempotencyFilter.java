package com.veylor.relay.security;

import com.veylor.relay.entity.IdempotentRequest;
import com.veylor.relay.repository.IdempotentRequestRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(2)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotentRequestRepository idempotentRequestRepository;

    public IdempotencyFilter(IdempotentRequestRepository idempotentRequestRepository) {
        this.idempotentRequestRepository = idempotentRequestRepository;
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
        if (nonceHeader != null && !nonceHeader.trim().isEmpty()) {
            try {
                UUID nonce = UUID.fromString(nonceHeader.trim());

                // Check if already completed
                Optional<IdempotentRequest> existingOpt = idempotentRequestRepository.findById(nonce);
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
                    // Already reserved but not completed - allow processing to continue
                } else {
                    // Reserve the nonce
                    IdempotentRequest record = IdempotentRequest.builder()
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
                markCompleted(nonce, responseWrapper.getStatus(), responseWrapper.getCaptureAsString());
            } catch (Exception e) {
                // Mark as completed with error status
                UUID nonce = UUID.fromString(nonceHeader.trim());
                markCompleted(nonce, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "{\"error\":\"Internal server error\"}");
                throw e;
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Transactional
    protected void markCompleted(UUID nonce, int statusCode, String responseBody) {
        idempotentRequestRepository.findById(nonce).ifPresent(record -> {
            record.setCompleted(true);
            record.setStatusCode(statusCode);
            record.setResponseBody(responseBody);
            idempotentRequestRepository.save(record);
        });
    }
}
