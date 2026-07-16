package com.veylor.relay.schemas;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
    // Convenient static helper factory methods
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Operation successful", data);
    }

    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(true, message, null);
    }
}