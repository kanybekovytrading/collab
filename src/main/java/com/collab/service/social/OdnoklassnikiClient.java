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
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.TreeMap;

/**
 * OK.ru (Одноклассники) API.
 * Для публичных профилей не требует OAuth — нужны только application_key и secret_key.
 * Создать приложение: https://ok.ru/devaccess
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.social.ok.application-key")
public class OdnoklassnikiClient implements SocialPlatformClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.social.ok.application-key}")
    private String applicationKey;

    @Value("${app.social.ok.secret-key}")
    private String secretKey;

    @Value("${app.social.ok.access-token}")
    private String accessToken;

    @Override
    public SocialAccount.Platform platform() {
        return SocialAccount.Platform.ODNOKLASSNIKI;
    }

    @Override
    public Optional<Long> fetchByUsername(String username) {
        try {
            // Сначала получаем uid по username через users.getInfo
            TreeMap<String, String> params = new TreeMap<>();
            params.put("method", "users.getInfo");
            params.put("uids", username);
            params.put("fields", "uid,follower_count");
            params.put("application_key", applicationKey);
            params.put("access_token", accessToken);
            params.put("format", "json");

            String sig = buildSig(params);
            params.put("sig", sig);

            StringBuilder urlBuilder = new StringBuilder("https://api.ok.ru/fb.do?");
            params.forEach((k, v) -> urlBuilder.append(k).append("=").append(v).append("&"));
            String url = urlBuilder.toString();

            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Optional.empty();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("error_code")) {
                    log.warn("OK.ru API error for {}: {} - {}",
                            username,
                            root.path("error_code").asText(),
                            root.path("error_msg").asText());
                    return Optional.empty();
                }
                // response is array of users
                if (root.isArray() && !root.isEmpty()) {
                    long count = root.get(0).path("follower_count").asLong(-1);
                    return count >= 0 ? Optional.of(count) : Optional.empty();
                }
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("OK.ru fetch failed for {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Подпись запроса по алгоритму OK.ru:
     * sorted_params_string + md5(access_token + secret_key)
     */
    private String buildSig(TreeMap<String, String> params) {
        StringBuilder paramStr = new StringBuilder();
        params.forEach((k, v) -> {
            if (!k.equals("access_token") && !k.equals("sig")) {
                paramStr.append(k).append("=").append(v);
            }
        });
        String tokenSecret = DigestUtils.md5DigestAsHex(
                (accessToken + secretKey).getBytes(StandardCharsets.UTF_8)).toUpperCase();
        return DigestUtils.md5DigestAsHex(
                (paramStr + tokenSecret).getBytes(StandardCharsets.UTF_8)).toUpperCase();
    }
}