package com.voicecloning.poc.service;

import com.voicecloning.poc.client.TtsServiceClient;
import com.voicecloning.poc.config.AppProperties;
import com.voicecloning.poc.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class VoiceGenerationService {

    private static final Logger log = LoggerFactory.getLogger(VoiceGenerationService.class);

    private final StorageService storageService;
    private final TtsServiceClient ttsServiceClient;
    private final AppProperties.Upload uploadRules;

    public VoiceGenerationService(StorageService storageService,
                                   TtsServiceClient ttsServiceClient,
                                   AppProperties appProperties) {
        this.storageService = storageService;
        this.ttsServiceClient = ttsServiceClient;
        this.uploadRules = appProperties.getUpload();
    }

    /**
     * Validates the request, forwards it to the tts-service, stores the
     * result, and returns the id the generated file was stored under.
     */
    public String generate(MultipartFile referenceAudio, String referenceText, String text) {
        validate(referenceAudio, referenceText, text);

        byte[] audioBytes;
        try {
            audioBytes = referenceAudio.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read the uploaded reference audio: " + e.getMessage());
        }

        log.info("Requesting generation ({} chars of text, {} byte reference clip)",
                text.length(), audioBytes.length);

        byte[] generatedWav = ttsServiceClient.generate(
                audioBytes, referenceAudio.getOriginalFilename(), referenceText.trim(), text.trim());

        try {
            String id = storageService.saveGeneratedAudio(generatedWav);
            log.info("Stored generated audio as {}", id);
            return id;
        } catch (IOException e) {
            throw new ValidationException("Failed to store generated audio: " + e.getMessage());
        }
    }

    private void validate(MultipartFile referenceAudio, String referenceText, String text) {
        if (referenceAudio == null || referenceAudio.isEmpty()) {
            throw new ValidationException("referenceAudio is required.");
        }
        if (referenceAudio.getSize() > uploadRules.maxAudioBytes()) {
            throw new ValidationException(
                    "referenceAudio exceeds the maximum allowed size of " +
                            (uploadRules.maxAudioBytes() / (1024 * 1024)) + " MB.");
        }
        String ext = extensionOf(referenceAudio.getOriginalFilename());
        List<String> allowed = uploadRules.allowedAudioExtensions();
        if (ext == null || !allowed.contains(ext)) {
            throw new ValidationException(
                    "Unsupported reference audio format" + (ext != null ? " '" + ext + "'" : "") +
                            ". Supported formats: " + String.join(", ", allowed));
        }
        if (referenceText == null || referenceText.isBlank()) {
            throw new ValidationException("referenceText is required.");
        }
        if (referenceText.length() > uploadRules.maxReferenceTextLength()) {
            throw new ValidationException(
                    "referenceText exceeds the maximum length of " + uploadRules.maxReferenceTextLength() + " characters.");
        }
        if (text == null || text.isBlank()) {
            throw new ValidationException("text is required.");
        }
        if (text.length() > uploadRules.maxTextLength()) {
            throw new ValidationException(
                    "text exceeds the maximum length of " + uploadRules.maxTextLength() + " characters.");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
