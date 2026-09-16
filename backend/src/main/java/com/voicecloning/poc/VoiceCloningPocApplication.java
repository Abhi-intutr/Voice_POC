package com.voicecloning.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VoiceCloningPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceCloningPocApplication.class, args);
    }
}
