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

/**
 * Получает кол-во участников публичного Telegram канала/группы.
 * Бот должен быть создан через @BotFather.
 * Для публичных каналов бот НЕ обязательно должен быть участником.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.social.telegram.bot-token")
public class TelegramClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.social.telegram.bot-token}")
    private String botToken;

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.TELEGRAM;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        // Telegram требует @ перед username для публичных каналов
        String chatId = username.startsWith("@") ? username : "@" + username;
        String url = "https://api.telegram.org/bot" + botToken
                + "/getChatMemberCount?chat_id=" + chatId;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (!root.path("ok").asBoolean()) {
                    log.warn("Telegram API error for {}: {}", username, root.path("description").asText());
                    return Optional.empty();
                }
                long count = root.path("result").asLong(-1);
                return count >= 0 ? Optional.of(count) : Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Telegram fetch failed for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }
}