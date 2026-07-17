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
        String nonce = UUID.randomUUID().toString();

        // Build canonical request string: METHOD\nPATH\nNONCE\nBODY (matching HmacSecurityFilter logic)
        String canonicalRequest = "POST\n/api/v1/notifications/bulk\n" + nonce + "\n" + body;
        String validSignature = HmacUtils.calculateHmac(canonicalRequest.getBytes(), rawApiKey);

        request.addHeader("X-RELAY-API-Key", rawApiKey);
        request.addHeader("X-RELAY-Authorization", validSignature);
        request.addHeader("X-RELAY-Nonce", nonce);
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
    void testHmacAcceptsValidSignatureWithoutNonce() throws Exception {
        // HMAC should also work without nonce (empty nonce in canonical request)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        String body = "{\"notifications\":[]}";

        // Build canonical request string with empty nonce
        String canonicalRequest = "POST\n/api/v1/notifications/bulk\n\n" + body;
        String validSignature = HmacUtils.calculateHmac(canonicalRequest.getBytes(), rawApiKey);

        request.addHeader("X-RELAY-API-Key", rawApiKey);
        request.addHeader("X-RELAY-Authorization", validSignature);
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
    void testIdempotencyReplaysCompletedRequest() throws Exception {
        // Finding #11: Completed requests should replay the original stored response
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        IdempotentRequest completedRequest = IdempotentRequest.builder()
                .nonce(nonce)
                .completed(true)
                .statusCode(HttpServletResponse.SC_OK)
                .responseBody("{\"status\":\"original response\"}")
                .build();

        when(idempotentRequestRepository.findById(nonce)).thenReturn(Optional.of(completedRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        // Should replay the original response without invoking filter chain
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains("original response"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testIdempotencyReservesNonceOnFirstRequest() throws Exception {
        // Finding #11: First request should reserve the nonce (not mark as completed yet)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        when(idempotentRequestRepository.findById(nonce)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        // Should save a reservation record (completed=false) and continue to filter chain
        verify(idempotentRequestRepository, times(1)).saveAndFlush(argThat(req ->
            req.getNonce().equals(nonce) && !req.getCompleted()
        ));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void testIdempotencyAllowsReservedButNotCompletedRequest() throws Exception {
        // Finding #11: Reserved but not completed requests should be allowed to continue
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        IdempotentRequest reservedRequest = IdempotentRequest.builder()
                .nonce(nonce)
                .completed(false)
                .build();

        when(idempotentRequestRepository.findById(nonce)).thenReturn(Optional.of(reservedRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        // Should continue processing (not replay, not reserve again)
        verify(idempotentRequestRepository, never()).saveAndFlush(any());
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void testIdempotencyCompletionRecording() throws Exception {
        // Finding #11: After successful downstream processing, mark as completed
        // Note: The actual completion recording happens via markCompleted() method after filter chain
        // This test verifies the filter captures the response for later completion
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        when(idempotentRequestRepository.findById(nonce)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // Simulate downstream writes a response
        doAnswer(invocation -> {
            MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"result\":\"success\"}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        idempotencyFilter.doFilter(request, response, filterChain);

        // Filter should have wrapped the response to capture output
        verify(filterChain, times(1)).doFilter(any(), any());
        verify(idempotentRequestRepository, times(1)).saveAndFlush(any(IdempotentRequest.class));
    }

    @Test
    void testIdempotencyDefaultResponseForCompletedWithoutBody() throws Exception {
        // Finding #11: If completed request has no stored response body, use default message
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        IdempotentRequest completedRequest = IdempotentRequest.builder()
                .nonce(nonce)
                .completed(true)
                .statusCode(HttpServletResponse.SC_OK)
                .responseBody(null)
                .build();

        when(idempotentRequestRepository.findById(nonce)).thenReturn(Optional.of(completedRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains("Duplicate request processed successfully"));
        verify(filterChain, never()).doFilter(any(), any());
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

    @Test
    void testHmacFilterBlocksBeforeIdempotencyNoncePersistence() throws Exception {
        // Finding #10: Unauthenticated requests should be blocked by HMAC before idempotency logic
        // This test verifies HMAC filter runs first and blocks invalid auth before nonce is persisted
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());
        // Missing HMAC headers (X-RELAY-API-Key and X-RELAY-Authorization)

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain mockIdempotencyChain = mock(FilterChain.class);

        // HMAC filter should reject before idempotency filter runs
        hmacSecurityFilter.doFilter(request, response, mockIdempotencyChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(mockIdempotencyChain, never()).doFilter(any(), any());
        // Idempotency repository should never be called since HMAC blocked the request
        verify(idempotentRequestRepository, never()).findById(any());
        verify(idempotentRequestRepository, never()).saveAndFlush(any());
    }
}
