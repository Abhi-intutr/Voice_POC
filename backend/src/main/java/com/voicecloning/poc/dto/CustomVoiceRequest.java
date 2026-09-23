package com.voicecloning.poc.dto;

public record CustomVoiceRequest(
        String text,
        String speaker,
        String language,
        String instruct
) {
}
