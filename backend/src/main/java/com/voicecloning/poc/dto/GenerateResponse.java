package com.voicecloning.poc.dto;

public record GenerateResponse(boolean success, String audioUrl) {
    public static GenerateResponse of(String audioId) {
        return new GenerateResponse(true, "/api/audio/" + audioId);
    }
}
