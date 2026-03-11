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
 * Instagram Graph API — требует OAuth access token пользователя.
 * Пользователь должен авторизовать приложение через Facebook Login.
 *
 * Флоу получения токена:
 * 1. Редирект на: https://www.facebook.com/v21.0/dialog/oauth?client_id={app_id}&redirect_uri={uri}&scope=instagram_basic
 * 2. Получаешь code → меняешь на short-lived token → меняешь на long-lived token (60 дней)
 * 3. Сохраняешь token в SocialAccount.accessToken
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InstagramClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.INSTAGRAM;
    }

    @Override
    public boolean requiresOAuth() {
        return true;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        // Instagram не предоставляет публичный API без OAuth
        return Optional.empty();
    }

    @Override
    public Optional<Long> fetchByToken(String accessToken) {
        String url = "https://graph.instagram.com/v21.0/me"
                + "?fields=followers_count&access_token=" + accessToken;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("error")) {
                    log.warn("Instagram API error: {}", root.path("error").path("message").asText());
                    return Optional.empty();
                }
                long count = root.path("followers_count").asLong(-1);
                return count >= 0 ? Optional.of(count) : Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Instagram fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}