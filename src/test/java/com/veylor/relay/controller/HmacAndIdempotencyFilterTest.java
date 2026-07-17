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
import org.mockito.ArgumentCaptor;
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

        Application app = Application.builder().id(UUID.randomUUID()).name("Test App").build();
        request.setAttribute("authenticatedApplication", app);

        IdempotentRequest completedRequest = IdempotentRequest.builder()
                .application(app)
                .nonce(nonce)
                .completed(true)
                .statusCode(HttpServletResponse.SC_OK)
                .responseBody("{\"status\":\"original response\"}")
                .build();

        when(idempotentRequestRepository.findByApplicationIdAndNonce(app.getId(), nonce)).thenReturn(Optional.of(completedRequest));

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

        Application app = Application.builder().id(UUID.randomUUID()).name("Test App").build();
        request.setAttribute("authenticatedApplication", app);

        when(idempotentRequestRepository.findByApplicationIdAndNonce(app.getId(), nonce)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        // Should save a reservation record (completed=false) and continue to filter chain
        verify(idempotentRequestRepository, times(1)).saveAndFlush(argThat(req ->
            req.getNonce().equals(nonce) && req.getApplication().getId().equals(app.getId()) && !req.getCompleted()
        ));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void testIdempotencyRejectsIncompleteConcurrentRetries() throws Exception {
        // Finding #11: Incomplete concurrent retries should be rejected with 409 Conflict
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        Application app = Application.builder().id(UUID.randomUUID()).name("Test App").build();
        request.setAttribute("authenticatedApplication", app);

        IdempotentRequest reservedRequest = IdempotentRequest.builder()
                .application(app)
                .nonce(nonce)
                .completed(false)
                .build();

        when(idempotentRequestRepository.findByApplicationIdAndNonce(app.getId(), nonce)).thenReturn(Optional.of(reservedRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        idempotencyFilter.doFilter(request, response, filterChain);

        // Should return 409 Conflict without invoking the filter chain
        assertEquals(HttpServletResponse.SC_CONFLICT, response.getStatus());
        assertTrue(response.getContentAsString().contains("already in progress"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testIdempotencyCompletionRecording() throws Exception {
        // Finding #11: After successful downstream processing, capture and mark as completed
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        Application app = Application.builder().id(UUID.randomUUID()).name("Test App").build();
        request.setAttribute("authenticatedApplication", app);

        IdempotentRequest reservedRequest = IdempotentRequest.builder()
                .application(app)
                .nonce(nonce)
                .completed(false)
                .build();

        when(idempotentRequestRepository.findByApplicationIdAndNonce(app.getId(), nonce))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(reservedRequest));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // Simulate downstream writes a response
        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse resp = (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"result\":\"success\"}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());

        // Capture initial reservation and completion save
        ArgumentCaptor<IdempotentRequest> reserveCaptor = ArgumentCaptor.forClass(IdempotentRequest.class);
        verify(idempotentRequestRepository, times(1)).saveAndFlush(reserveCaptor.capture());
        assertFalse(reserveCaptor.getValue().getCompleted());

        ArgumentCaptor<IdempotentRequest> completedCaptor = ArgumentCaptor.forClass(IdempotentRequest.class);
        verify(idempotentRequestRepository, times(1)).save(completedCaptor.capture());
        assertTrue(completedCaptor.getValue().getCompleted());
        assertEquals(HttpServletResponse.SC_OK, completedCaptor.getValue().getStatusCode());
        assertEquals("{\"result\":\"success\"}", completedCaptor.getValue().getResponseBody());
    }

    @Test
    void testIdempotencyDefaultResponseForCompletedWithoutBody() throws Exception {
        // Finding #11: If completed request has no stored response body, use default message
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications/bulk");
        UUID nonce = UUID.randomUUID();
        request.addHeader("X-RELAY-Nonce", nonce.toString());

        Application app = Application.builder().id(UUID.randomUUID()).name("Test App").build();
        request.setAttribute("authenticatedApplication", app);

        IdempotentRequest completedRequest = IdempotentRequest.builder()
                .application(app)
                .nonce(nonce)
                .completed(true)
                .statusCode(HttpServletResponse.SC_OK)
                .responseBody(null)
                .build();

        when(idempotentRequestRepository.findByApplicationIdAndNonce(app.getId(), nonce)).thenReturn(Optional.of(completedRequest));

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
        // Production Order: HmacSecurityFilter runs first, then IdempotencyFilter
        org.springframework.test.web.servlet.MockMvc mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new Object())
                .addFilters(hmacSecurityFilter, idempotencyFilter)
                .build();

        UUID nonce = UUID.randomUUID();

        // Send unauthenticated request (missing HMAC signatures)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/notifications/bulk")
                .header("X-RELAY-Nonce", nonce.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());

        // Verify HMAC blocked the request so idempotency repository was never touched
        verify(idempotentRequestRepository, never()).findByApplicationIdAndNonce(any(), any());
        verify(idempotentRequestRepository, never()).saveAndFlush(any());

        // Verify reverse registration order breaks execution expectation (idempotency runs without authenticatedApplication)
        org.springframework.test.web.servlet.MockMvc reverseMockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new Object())
                .addFilters(idempotencyFilter, hmacSecurityFilter)
                .build();

        reverseMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/notifications/bulk")
                .header("X-RELAY-Nonce", nonce.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());

        // In reverse order, idempotency bypassed it because application was null
        verify(idempotentRequestRepository, never()).findByApplicationIdAndNonce(any(), any());
    }
}
