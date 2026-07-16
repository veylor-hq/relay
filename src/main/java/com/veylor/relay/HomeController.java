package com.veylor.relay;

import com.veylor.relay.schemas.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/")
    public ResponseEntity<ApiResponse<Void>> home() {
        return ResponseEntity
                .ok()
                .body(new ApiResponse<>(true, "Veylor Relay - Operational", null));
    }
}