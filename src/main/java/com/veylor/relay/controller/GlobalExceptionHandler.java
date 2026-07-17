package com.veylor.relay.controller;

import com.veylor.relay.schemas.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        ApiResponse<Void> errorResponse = ApiResponse.errorMessage(
                "Route not found " + ex.getResourcePath()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException ex) {
        ApiResponse<Void> errorResponse = ApiResponse.errorMessage(
                "Route not found " + ex.getRequestURL()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }
}