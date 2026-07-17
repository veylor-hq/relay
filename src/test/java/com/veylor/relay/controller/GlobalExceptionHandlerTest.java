package com.veylor.relay.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class DummyController {
        @GetMapping("/trigger-no-resource")
        public void triggerNoResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "Resource not found", "/some/static/file.js");
        }

        @GetMapping("/trigger-no-handler")
        public void triggerNoHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/api/v1/invalid", HttpHeaders.EMPTY);
        }

        @GetMapping("/trigger-generic")
        public void triggerGeneric() {
            throw new RuntimeException("Database error or similar");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DummyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void testDirectHandleNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "Resource not found", "/some/static/file.js");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleNoResourceFound(ex);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertEquals("Route not found /some/static/file.js", response.getBody().message());
    }

    @Test
    void testHandleNoResourceFound() throws Exception {
        mockMvc.perform(get("/trigger-no-resource"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Route not found /some/static/file.js"));
    }

    @Test
    void testHandleNoHandlerFound() throws Exception {
        mockMvc.perform(get("/trigger-no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Route not found /api/v1/invalid"));
    }

    @Test
    void testHandleGenericException() throws Exception {
        mockMvc.perform(get("/trigger-generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
