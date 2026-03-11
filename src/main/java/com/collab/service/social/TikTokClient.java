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
 * TikTok for Developers API — требует OAuth access token пользователя.
 * Необходимо одобрение приложения на https://developers.tiktok.com
 *
 * Флоу получения токена:
 * 1. Редирект на: https://www.tiktok.com/v2/auth/authorize/?client_key={key}&scope=user.info.basic&response_type=code&redirect_uri={uri}
 * 2. Получаешь code → POST на https://open.tiktokapis.com/v2/oauth/token/ → access_token
 * 3. Сохраняешь token в SocialAccount.accessToken
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TikTokClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.TIKTOK;
    }

    @Override
    public boolean requiresOAuth() {
        return true;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        // TikTok не предоставляет публичный API без OAuth
        return Optional.empty();
    }

    @Override
    public Optional<Long> fetchByToken(String accessToken) {
        String url = "https://open.tiktokapis.com/v2/user/info/?fields=follower_count";
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.path("error").path("code").asText("ok").equals("ok") == false) {
                    log.warn("TikTok API error: {}", root.path("error").path("message").asText());
                    return Optional.empty();
                }
                long count = root.path("data").path("user").path("follower_count").asLong(-1);
                return count >= 0 ? Optional.of(count) : Optional.empty();
            }
        } catch (Exception e) {
            log.warn("TikTok fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}