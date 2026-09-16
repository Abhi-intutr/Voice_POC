package com.voicecloning.poc.controller;

import com.voicecloning.poc.exception.ValidationException;
import com.voicecloning.poc.service.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class AudioController {

    private final StorageService storageService;

    public AudioController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/api/audio/{id}")
    public ResponseEntity<Resource> getAudio(@PathVariable String id) {
        Path path = storageService.resolveGeneratedAudio(id);
        Resource resource = new FileSystemResource(path);

        long contentLength;
        try {
            contentLength = Files.size(path);
        } catch (IOException e) {
            throw new ValidationException("Could not read generated audio file: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".wav\"")
                .contentLength(contentLength)
                .body(resource);
    }
}
