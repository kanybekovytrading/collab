package com.collab.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String json = System.getenv("FIREBASE_CREDENTIALS_JSON");

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
                );

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.warn("Firebase init failed (push notifications disabled): {}", e.getMessage());
        }
    }
}
