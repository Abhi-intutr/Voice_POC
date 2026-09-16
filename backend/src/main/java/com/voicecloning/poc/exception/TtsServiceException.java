package com.voicecloning.poc.exception;

/**
 * Thrown when the Python tts-service can't be reached, isn't ready yet, or
 * returns an error while generating audio.
 */
public class TtsServiceException extends RuntimeException {

    private final boolean unreachable;

    public TtsServiceException(String message, boolean unreachable) {
        super(message);
        this.unreachable = unreachable;
    }

    public TtsServiceException(String message, Throwable cause, boolean unreachable) {
        super(message, cause);
        this.unreachable = unreachable;
    }

    /** true if we couldn't connect at all; false if it responded with an error status. */
    public boolean isUnreachable() {
        return unreachable;
    }
}
