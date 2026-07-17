package com.veylor.relay.controller;

import com.veylor.relay.entity.Application;
import com.veylor.relay.repository.ApplicationRepository;
import com.veylor.relay.util.HmacUtils;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationRepository applicationRepository;

    @PostMapping
    public ResponseEntity<CreateApplicationResponse> createApplication(@RequestBody CreateApplicationRequest request) {
        String rawApiKey = "vlr_" + UUID.randomUUID().toString().replace("-", "");
        String accessKeyHash = HmacUtils.sha256Hex(rawApiKey);

        Application application = Application.builder()
                .name(request.getName())
                .accessKeyHash(accessKeyHash)
                .build();

        Application saved = applicationRepository.save(application);

        CreateApplicationResponse response = CreateApplicationResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .apiKey(rawApiKey)
                .createdAt(saved.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        List<ApplicationResponse> applications = applicationRepository.findAll().stream()
                .map(app -> ApplicationResponse.builder()
                        .id(app.getId())
                        .name(app.getName())
                        .createdAt(app.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        return applicationRepository.findById(id)
                .map(app -> ApplicationResponse.builder()
                        .id(app.getId())
                        .name(app.getName())
                        .createdAt(app.getCreatedAt())
                        .build())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(@PathVariable UUID id, @RequestBody UpdateApplicationRequest request) {
        return applicationRepository.findById(id)
                .map(app -> {
                    app.setName(request.getName());
                    Application saved = applicationRepository.save(app);
                    return ResponseEntity.ok(ApplicationResponse.builder()
                            .id(saved.getId())
                            .name(saved.getName())
                            .createdAt(saved.getCreatedAt())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable UUID id) {
        if (applicationRepository.existsById(id)) {
            applicationRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateApplicationRequest {
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateApplicationResponse {
        private UUID id;
        private String name;
        private String apiKey;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateApplicationRequest {
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApplicationResponse {
        private UUID id;
        private String name;
        private Instant createdAt;
    }
}
