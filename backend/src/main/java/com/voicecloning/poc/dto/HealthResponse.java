package com.voicecloning.poc.dto;

public record HealthResponse(
        String backendStatus,
        String ttsServiceStatus,
        String ttsModel,
        String detail
) {
}
