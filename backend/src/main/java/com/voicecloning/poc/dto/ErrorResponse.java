package com.voicecloning.poc.dto;

public record ErrorResponse(boolean success, String error) {
    public static ErrorResponse of(String error) {
        return new ErrorResponse(false, error);
    }
}
