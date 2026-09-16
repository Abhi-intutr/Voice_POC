package com.voicecloning.poc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds every "app.*" entry from application.yml. Keeping this in one place
 * makes it obvious what's configurable for this POC (no database, no
 * external config server, just one YAML file).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage;
    private final Tts tts;
    private final Cors cors;
    private final Upload upload;

    public AppProperties(Storage storage, Tts tts, Cors cors, Upload upload) {
        this.storage = storage;
        this.tts = tts;
        this.cors = cors;
        this.upload = upload;
    }

    public Storage getStorage() {
        return storage;
    }

    public Tts getTts() {
        return tts;
    }

    public Cors getCors() {
        return cors;
    }

    public Upload getUpload() {
        return upload;
    }

    public record Storage(String uploadDir, String generatedDir) {
    }

    public record Tts(String baseUrl, int timeoutSeconds) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Upload(
            long maxAudioBytes,
            List<String> allowedAudioExtensions,
            int maxTextLength,
            int maxReferenceTextLength
    ) {
    }
}
