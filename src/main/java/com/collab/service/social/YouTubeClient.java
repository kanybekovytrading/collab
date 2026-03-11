package com.collab.service.social;

import com.collab.entity.SocialAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.social.youtube.api-key")
public class YouTubeClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.social.youtube.api-key}")
    private String apiKey;

    private static final String BASE = "https://www.googleapis.com/youtube/v3/channels";

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.YOUTUBE;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        if (apiKey.startsWith("YOUR_")) {
            log.warn("YouTube API key not configured. Set app.social.youtube.api-key in application.yml");
            return Optional.empty();
        }
        // Пробуем сначала по handle (@username), потом по forUsername
        String handle = username.startsWith("@") ? username : "@" + username;
        Optional<Long> result = fetch(BASE + "?forHandle=" + handle + "&part=statistics&key=" + apiKey);
        if (result.isPresent()) return result;
        return fetch(BASE + "?forUsername=" + username + "&part=statistics&key=" + apiKey);
    }

    private Optional<Long> fetch(String url) {
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) return Optional.empty();
                long subscribers = items.get(0).path("statistics").path("subscriberCount").asLong(-1);
                return subscribers >= 0 ? Optional.of(subscribers) : Optional.empty();
            }
        } catch (Exception e) {
            log.warn("YouTube fetch failed for url {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}