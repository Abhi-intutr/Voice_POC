package com.voicecloning.poc.controller;

import com.voicecloning.poc.dto.GenerateResponse;
import com.voicecloning.poc.service.VoiceGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VoiceController {

    private final VoiceGenerationService voiceGenerationService;

    public VoiceController(VoiceGenerationService voiceGenerationService) {
        this.voiceGenerationService = voiceGenerationService;
    }

    @PostMapping(value = "/api/voice/generate", consumes = "multipart/form-data")
    public ResponseEntity<GenerateResponse> generate(
            @RequestParam("referenceAudio") MultipartFile referenceAudio,
            @RequestParam("referenceText") String referenceText,
            @RequestParam("text") String text
    ) {
        String id = voiceGenerationService.generate(referenceAudio, referenceText, text);
        return ResponseEntity.ok(GenerateResponse.of(id));
    }
}
