package com.voicecloning.poc.controller;

import com.voicecloning.poc.client.TtsServiceClient;
import com.voicecloning.poc.dto.HealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final TtsServiceClient ttsServiceClient;

    public HealthController(TtsServiceClient ttsServiceClient) {
        this.ttsServiceClient = ttsServiceClient;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        TtsServiceClient.TtsHealth ttsHealth = ttsServiceClient.health();
        boolean ttsReady = ttsHealth.reachable() && "ready".equals(ttsHealth.status());

        HealthResponse response = new HealthResponse(
                "UP",
                ttsHealth.status(),
                ttsHealth.model(),
                ttsHealth.detail()
        );

        return ResponseEntity.status(ttsReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
