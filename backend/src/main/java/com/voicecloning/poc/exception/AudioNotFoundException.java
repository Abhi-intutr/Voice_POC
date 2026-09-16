package com.voicecloning.poc.exception;

/** Thrown when GET /api/audio/{id} does not match a stored generated file. */
public class AudioNotFoundException extends RuntimeException {
    public AudioNotFoundException(String id) {
        super("No generated audio found for id: " + id);
    }
}
