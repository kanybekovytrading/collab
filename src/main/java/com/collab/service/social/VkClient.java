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
@ConditionalOnProperty("app.social.vk.service-token")
public class VkClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.social.vk.service-token}")
    private String serviceToken;

    private static final String API = "https://api.vk.com/method/";
    private static final String VERSION = "5.199";

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.VKONTAKTE;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        // Пробуем как публичный пользователь
        Optional<Long> user = fetchUser(username);
        if (user.isPresent()) return user;
        // Пробуем как группу/сообщество
        return fetchGroup(username);
    }

    private Optional<Long> fetchUser(String username) {
        String url = API + "users.get?user_ids=" + username
                + "&fields=followers_count&v=" + VERSION
                + "&access_token=" + serviceToken;
        try {
            JsonNode response = call(url);
            if (response == null) return Optional.empty();
            JsonNode items = response.path("response");
            if (!items.isArray() || items.isEmpty()) return Optional.empty();
            JsonNode user = items.get(0);
            if (user.has("deactivated")) return Optional.empty();
            long count = user.path("followers_count").asLong(-1);
            return count >= 0 ? Optional.of(count) : Optional.empty();
        } catch (Exception e) {
            log.warn("VK users.get failed for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> fetchGroup(String username) {
        String url = API + "groups.getById?group_id=" + username
                + "&fields=members_count&v=" + VERSION
                + "&access_token=" + serviceToken;
        try {
            JsonNode response = call(url);
            if (response == null) return Optional.empty();
            JsonNode items = response.path("response").path("groups");
            if (!items.isArray() || items.isEmpty()) return Optional.empty();
            long count = items.get(0).path("members_count").asLong(-1);
            return count >= 0 ? Optional.of(count) : Optional.empty();
        } catch (Exception e) {
            log.warn("VK groups.getById failed for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode call(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JsonNode root = objectMapper.readTree(response.body().string());
            if (root.has("error")) {
                log.warn("VK API error: {}", root.path("error").path("error_msg").asText());
                return null;
            }
            return root;
        }
    }
}