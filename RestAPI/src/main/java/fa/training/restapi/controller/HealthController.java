package fa.training.restapi.controller;

import fa.training.restapi.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("App running successfully")
                .result("OKE")
                .build();

        return ResponseEntity.ok(response);
    }
}