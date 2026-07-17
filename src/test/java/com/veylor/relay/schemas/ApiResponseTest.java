package com.veylor.relay.schemas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testErrorFactoryMethodSetsSuccessFalse() {
        ApiResponse<String> response = ApiResponse.error("Failed details");
        assertFalse(response.success());
        assertEquals("Operation failed", response.message());
        assertEquals("Failed details", response.data());
    }

    @Test
    void testSuccessFactoryMethodSetsSuccessTrue() {
        ApiResponse<String> response = ApiResponse.success("Success details");
        assertTrue(response.success());
        assertEquals("Operation successful", response.message());
        assertEquals("Success details", response.data());
    }
}
