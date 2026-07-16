package com.veylor.relay.controller;

import com.veylor.relay.entity.Application;
import com.veylor.relay.entity.IdempotentRequest;
import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.repository.IdempotentRequestRepository;
import com.veylor.relay.security.AdminSecurityInterceptor;
import com.veylor.relay.security.HmacSecurityFilter;
import com.veylor.relay.security.IdempotencyFilter;
import com.veylor.relay.util.HmacUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HmacAndIdempotencyFilterTest {

    private ApplicationRepository applicationRepository;
    private IdempotentRequestRepository idempotentRequestRepository;

    private HmacSecurityFilter hmacSecurityFilter;
    private IdempotencyFilter idempotencyFilter;
    private AdminSecurityInterceptor adminSecurityInterceptor;

    private final String rawApiKey = "vlr_testkey123456789";

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        idempotentRequestRepository = mock(IdempotentRequestRepository.class);

        hmacSecurityFilter = new HmacSecurityFilter(applicationRepository);
        idempotencyFilter = new IdempotencyFilter(idempotentRequestRepository);

        adminSecurityInterceptor = new AdminSecurityInterceptor();
        ReflectionTestUtils.setField(adminSecurityInterceptor, "adminSecretToken", "admin_dev_token_12345");
    }

    @Test
    void testHmacRejectsMissingHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        hmacSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testHmacRejectsInvalidSignature() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        request.addHeader("X-RELAY-API-Key", rawApiKey);
        request.addHeader("X-RELAY-Authorization", "wrong_signature");
        request.setContent("{}".getBytes());

        String accessKeyHash = HmacUtils.sha256Hex(rawApiKey);
        Application application = Application.builder().name("Test App").accessKeyHash(accessKeyHash).build();
        when(applicationRepository.findByAccessKeyHash(accessKeyHash)).thenReturn(Optional.of(application));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        hmacSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testHmacAcceptsValidSignature() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        String body = "{\"notifications\":[]}";
        request.addHeader("X-RELAY-API-Key", rawApiKey);
        request.addHeader("X-RELAY-Authorization", HmacUtils.calculateHmac(body.getBytes(), rawApiKey));
        request.setContent(body.getBytes());

        String accessKeyHash = HmacUtils.sha256Hex(rawApiKey);
        Application application = Application.builder().name("Test App").accessKeyHash(accessKeyHash).build();
        when(applicationRepository.findByAccessKeyHash(accessKeyHash)).thenReturn(Optional.of(application));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        hmacSecurityFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void testIdempotencyBypassesOnDuplicate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        when(idempotentRequestRepository.existsById(nonce)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains("Duplicate request processed successfully"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testIdempotencySavesOnUnique() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        when(idempotentRequestRepository.existsById(nonce)).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(idempotentRequestRepository, times(1)).saveAndFlush(any(IdempotentRequest.class));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void testAdminSecurityInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/applications");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean noTokenResult = adminSecurityInterceptor.preHandle(request, response, new Object());
        assertFalse(noTokenResult);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());

        request.addHeader("X-Admin-Token", "admin_dev_token_12345");
        MockHttpServletResponse successResponse = new MockHttpServletResponse();
        boolean successResult = adminSecurityInterceptor.preHandle(request, successResponse, new Object());
        assertTrue(successResult);
    }

    @Test
    void testHmacSecuresRecipientsRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/recipients/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        hmacSecurityFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }
}
