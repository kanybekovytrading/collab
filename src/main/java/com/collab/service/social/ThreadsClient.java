package com.collab.service.social;

import com.collab.entity.SocialAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Meta Threads API — требует OAuth access token пользователя.
 * Приложение должно быть одобрено на https://developers.facebook.com
 *
 * Флоу получения токена:
 * 1. Редирект на: https://threads.net/oauth/authorize?client_id={app_id}&redirect_uri={uri}&scope=threads_basic&response_type=code
 * 2. Получаешь code → POST на https://graph.threads.net/oauth/access_token → short-lived token
 * 3. Меняешь на long-lived: GET https://graph.threads.net/access_token?grant_type=th_exchange_token&...
 * 4. Сохраняешь token в SocialAccount.accessToken
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThreadsClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.THREADS;
    }

    @Override
    public boolean requiresOAuth() {
        return true;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        // Threads не предоставляет публичный API без OAuth
        return Optional.empty();
    }

    @Override
    public Optional<Long> fetchByToken(String accessToken) {
        // GET /me?fields=followers_count
        String url = "https://graph.threads.net/v1.0/me"
                + "?fields=followers_count&access_token=" + accessToken;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("error")) {
                    log.warn("Threads API error: {}", root.path("error").path("message").asText());
                    return Optional.empty();
                }
                long count = root.path("followers_count").asLong(-1);
                return count >= 0 ? Optional.of(count) : Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Threads fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}