package com.voicecloning.poc.service;

import com.voicecloning.poc.config.AppProperties;
import com.voicecloning.poc.exception.AudioNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Flat-file storage for the POC: reference uploads go under voices/,
 * generated output goes under generated/. No database - files are looked
 * up by the UUID baked into their filename.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path uploadDir;
    private final Path generatedDir;

    public StorageService(AppProperties appProperties) {
        this.uploadDir = Paths.get(appProperties.getStorage().uploadDir()).toAbsolutePath().normalize();
        this.generatedDir = Paths.get(appProperties.getStorage().generatedDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
        Files.createDirectories(generatedDir);
        log.info("Reference audio directory: {}", uploadDir);
        log.info("Generated audio directory: {}", generatedDir);
    }

    /** Saves reference audio bytes and returns the id (also the filename stem) it was stored under. */
    public String saveReferenceAudio(byte[] bytes, String originalFilename) throws IOException {
        String id = UUID.randomUUID().toString();
        String ext = extensionOf(originalFilename);
        Path target = uploadDir.resolve(id + ext);
        Files.write(target, bytes);
        return id;
    }

    /** Saves generated WAV bytes under a fresh id and returns that id. */
    public String saveGeneratedAudio(byte[] wavBytes) throws IOException {
        String id = UUID.randomUUID().toString();
        Path target = generatedDir.resolve(id + ".wav");
        Files.write(target, wavBytes);
        return id;
    }

    /** Resolves a previously-generated audio file by id, or throws if it doesn't exist. */
    public Path resolveGeneratedAudio(String id) {
        // id comes straight from the URL path - guard against path traversal.
        if (id == null || !id.matches("[a-fA-F0-9-]{36}")) {
            throw new AudioNotFoundException(id);
        }
        Path candidate = generatedDir.resolve(id + ".wav").normalize();
        if (!candidate.startsWith(generatedDir) || !Files.exists(candidate)) {
            throw new AudioNotFoundException(id);
        }
        return candidate;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return ".wav";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".wav";
    }
}
