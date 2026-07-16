package com.veylor.relay.security;

import com.veylor.relay.entity.IdempotentRequest;
import com.veylor.relay.repository.IdempotentRequestRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
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
                if (idempotentRequestRepository.existsById(nonce)) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"success\",\"message\":\"Duplicate request processed successfully (idempotent)\"}");
                    return;
                }
                
                IdempotentRequest record = IdempotentRequest.builder()
                        .nonce(nonce)
                        .build();
                idempotentRequestRepository.saveAndFlush(record);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid UUID format for X-LINE-Authorization-Nonce");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
