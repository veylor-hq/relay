package com.veylor.relay.security;

import com.veylor.relay.entity.Application;
import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.util.HmacUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(1)
public class HmacSecurityFilter extends OncePerRequestFilter {

    private final ApplicationRepository applicationRepository;

    public HmacSecurityFilter(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/notifications") && !path.startsWith("/api/v1/recipients")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-RELAY-API-Key");
        String signature = request.getHeader("X-RELAY-Authorization");
        String nonce = request.getHeader("X-RELAY-Nonce");

        if (apiKey == null || signature == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing API Key or Signature");
            return;
        }

        String apiKeyHash = HmacUtils.sha256Hex(apiKey);
        Optional<Application> applicationOpt = applicationRepository.findByAccessKeyHash(apiKeyHash);

        if (applicationOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid API Key");
            return;
        }

        Application application = applicationOpt.get();

        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request);
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.getWriter().write("Payload Too Large: " + e.getMessage());
            return;
        }
        byte[] bodyBytes = cachedRequest.getCachedBody();

        // Build canonical request string: METHOD\nPATH\nNONCE\nBODY
        String method = request.getMethod();
        String canonicalRequest = method + "\n" + path + "\n" + (nonce != null ? nonce : "") + "\n" + new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
        String computedSignature = HmacUtils.calculateHmac(canonicalRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8), apiKey);

        if (!HmacUtils.constantTimeEquals(computedSignature, signature)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Signature verification failed");
            return;
        }

        request.setAttribute("authenticatedApplication", application);
        filterChain.doFilter(cachedRequest, response);
    }
}
