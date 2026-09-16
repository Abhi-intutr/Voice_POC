package com.voicecloning.poc.client;

import com.voicecloning.poc.config.AppProperties;
import com.voicecloning.poc.exception.TtsServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Talks to the separate Python tts-service over plain HTTP. Kept as the only
 * place in the backend that knows the wire format of that service, so the
 * rest of the app just deals in bytes/DTOs.
 */
@Component
public class TtsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TtsServiceClient.class);

    private final RestClient restClient;
    private final AppProperties.Tts ttsProperties;

    public TtsServiceClient(AppProperties appProperties) {
        this.ttsProperties = appProperties.getTts();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(ttsProperties.timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(ttsProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Calls POST /tts on the Python service and returns the raw WAV bytes.
     */
    public byte[] generate(byte[] referenceAudioBytes, String referenceAudioFilename,
                            String referenceText, String text) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("reference_audio", new ByteArrayResource(referenceAudioBytes) {
            @Override
            public String getFilename() {
                return referenceAudioFilename;
            }
        });
        body.add("reference_text", referenceText);
        body.add("text", text);

        try {
            return restClient.post()
                    .uri("/tts")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException ex) {
            String detail = extractErrorMessage(ex);
            log.warn("tts-service returned {} for /tts: {}", ex.getStatusCode(), detail);
            throw new TtsServiceException("TTS service rejected the request: " + detail, false);
        } catch (ResourceAccessException ex) {
            throw new TtsServiceException(
                    "Could not reach the TTS service at " + ttsProperties.baseUrl() +
                            ". Is tts-service running? (" + ex.getMessage() + ")", ex, true);
        }
    }

    /**
     * Calls GET /health on the Python service. Never throws - callers use the
     * returned status object to decide what to report.
     */
    public TtsHealth health() {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            String status = body != null ? String.valueOf(body.get("status")) : "unknown";
            String model = body != null ? String.valueOf(body.get("model")) : null;
            return new TtsHealth(true, status, model, null);
        } catch (RestClientResponseException ex) {
            // Service responded (possibly 503 while loading) - it's reachable,
            // just not ready. Try to pull structured detail out of the body.
            String detail = extractErrorMessage(ex);
            return new TtsHealth(true, "error", null, detail);
        } catch (ResourceAccessException ex) {
            return new TtsHealth(false, "unreachable", null, ex.getMessage());
        }
    }

    private String extractErrorMessage(RestClientResponseException ex) {
        try {
            String raw = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
            return raw == null || raw.isBlank() ? ex.getStatusText() : raw;
        } catch (Exception ignored) {
            return ex.getStatusText();
        }
    }

    public record TtsHealth(boolean reachable, String status, String model, String detail) {
    }
}
