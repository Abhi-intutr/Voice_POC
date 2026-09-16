package com.voicecloning.poc.exception;

/** Thrown for any request-validation failure; mapped to HTTP 400 by ApiExceptionHandler. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
