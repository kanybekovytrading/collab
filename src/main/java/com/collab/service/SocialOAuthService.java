package com.collab.service;

import com.collab.entity.BloggerProfile;
import com.collab.entity.BrandProfile;
import com.collab.entity.SocialAccount;
import com.collab.entity.User;
import com.collab.repository.BloggerProfileRepository;
import com.collab.repository.BrandProfileRepository;
import com.collab.repository.SocialAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialOAuthService {

    private final SocialAccountRepository socialAccountRepository;
    private final BloggerProfileRepository bloggerProfileRepository;
    private final BrandProfileRepository brandProfileRepository;
    private final SocialStatsService socialStatsService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.social.instagram.client-id:}")
    private String instagramClientId;
    @Value("${app.social.instagram.client-secret:}")
    private String instagramClientSecret;

    @Value("${app.social.tiktok.client-key:}")
    private String tiktokClientKey;
    @Value("${app.social.tiktok.client-secret:}")
    private String tiktokClientSecret;

    @Value("${app.social.threads.client-id:}")
    private String threadsClientId;
    @Value("${app.social.threads.client-secret:}")
    private String threadsClientSecret;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    // ─── Генерация OAuth URL ──────────────────────────────────────────────────

    public String buildAuthUrl(SocialAccount.Platform platform, UUID userId) {
        if (platform == SocialAccount.Platform.INSTAGRAM && instagramClientId.startsWith("YOUR_"))
            throw new IllegalStateException("Instagram client_id not configured. Set app.social.instagram.client-id in application.yml");
        if (platform == SocialAccount.Platform.TIKTOK && tiktokClientKey.startsWith("YOUR_"))
            throw new IllegalStateException("TikTok client_key not configured. Set app.social.tiktok.client-key in application.yml");
        if (platform == SocialAccount.Platform.THREADS && threadsClientId.startsWith("YOUR_"))
            throw new IllegalStateException("Threads client_id not configured. Set app.social.threads.client-id in application.yml");

        String state = encodeState(userId, platform);
        String redirectUri = callbackUri(platform);

        return switch (platform) {
            case INSTAGRAM -> "https://api.instagram.com/oauth/authorize"
                    + "?client_id=" + instagramClientId
                    + "&redirect_uri=" + redirectUri
                    + "&scope=user_profile,user_media"
                    + "&response_type=code"
                    + "&state=" + state;

            case TIKTOK -> "https://www.tiktok.com/v2/auth/authorize/"
                    + "?client_key=" + tiktokClientKey
                    + "&scope=user.info.basic"
                    + "&response_type=code"
                    + "&redirect_uri=" + redirectUri
                    + "&state=" + state;

            case THREADS -> "https://threads.net/oauth/authorize"
                    + "?client_id=" + threadsClientId
                    + "&redirect_uri=" + redirectUri
                    + "&scope=threads_basic"
                    + "&response_type=code"
                    + "&state=" + state;

            default -> throw new IllegalArgumentException("OAuth not supported for platform: " + platform);
        };
    }

    // ─── Обработка callback: code → token → сохранение ───────────────────────

    @Transactional
    public SocialAccount handleCallback(SocialAccount.Platform platform, String code, String state) {
        UUID userId = decodeUserId(state);
        String accessToken = exchangeCodeForToken(platform, code);

        SocialAccount account = findOrCreateAccount(platform, userId);
        account.setAccessToken(accessToken);
        SocialAccount saved = socialAccountRepository.save(account);

        // Сразу тянем подписчиков асинхронно
        socialStatsService.syncAccount(saved);
        return saved;
    }

    // ─── Обмен code на access token ──────────────────────────────────────────

    private String exchangeCodeForToken(SocialAccount.Platform platform, String code) {
        return switch (platform) {
            case INSTAGRAM -> exchangeInstagram(code);
            case TIKTOK    -> exchangeTikTok(code);
            case THREADS   -> exchangeThreads(code);
            default -> throw new IllegalArgumentException("OAuth not supported for: " + platform);
        };
    }

    private String exchangeInstagram(String code) {
        return exchangeInstagramWithRedirect(code, callbackUri(SocialAccount.Platform.INSTAGRAM));
    }

    /**
     * Обмен code на long-lived access token с произвольным redirect_uri.
     * Используется как для connect-флоу, так и для login-флоу (разные redirect_uri).
     */
    public String exchangeInstagramWithRedirect(String code, String redirectUri) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("client_id", instagramClientId)
                    .add("client_secret", instagramClientSecret)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", redirectUri)
                    .add("code", code)
                    .build();
            String shortToken = postForm("https://api.instagram.com/oauth/access_token", body, "access_token");

            // Меняем short-lived (1ч) на long-lived (60 дней)
            String longTokenUrl = "https://graph.instagram.com/access_token"
                    + "?grant_type=ig_exchange_token"
                    + "&client_secret=" + instagramClientSecret
                    + "&access_token=" + shortToken;
            return getField(longTokenUrl, "access_token");
        } catch (Exception e) {
            throw new RuntimeException("Instagram token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Получить профиль пользователя Instagram по access token.
     * Basic Display API возвращает: id, username, account_type.
     */
    public InstagramProfile fetchInstagramProfile(String accessToken) {
        try {
            String url = "https://graph.instagram.com/v21.0/me"
                    + "?fields=id,username,account_type"
                    + "&access_token=" + accessToken;
            Request request = new Request.Builder().url(url).get().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                    throw new RuntimeException("Instagram profile fetch failed: HTTP " + response.code());
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("error"))
                    throw new RuntimeException(root.path("error").path("message").asText());
                return new InstagramProfile(
                        root.path("id").asText(),
                        root.path("username").asText()
                );
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Instagram profile fetch failed: " + e.getMessage(), e);
        }
    }

    public record InstagramProfile(String id, String username) {}

    /**
     * Сохраняет Instagram access token в SocialAccount пользователя.
     * Вызывается после успешного Instagram-логина.
     */
    @Transactional
    public void saveInstagramToken(User user, String accessToken, String username) {
        SocialAccount account = socialAccountRepository
                .findByBloggerProfileUserIdAndPlatform(user.getId(), SocialAccount.Platform.INSTAGRAM)
                .or(() -> socialAccountRepository
                        .findByBrandProfileUserIdAndPlatform(user.getId(), SocialAccount.Platform.INSTAGRAM))
                .orElseGet(() -> {
                    // Создаём SocialAccount если его ещё нет
                    SocialAccount.SocialAccountBuilder builder = SocialAccount.builder()
                            .platform(SocialAccount.Platform.INSTAGRAM)
                            .username(username)
                            .accessToken(accessToken);

                    bloggerProfileRepository.findByUserId(user.getId())
                            .ifPresentOrElse(
                                    p -> builder.bloggerProfile(p),
                                    () -> brandProfileRepository.findByUserId(user.getId())
                                            .ifPresent(p -> builder.brandProfile(p))
                            );
                    return builder.build();
                });

        account.setAccessToken(accessToken);
        account.setUsername(username);
        SocialAccount saved = socialAccountRepository.save(account);
        socialStatsService.syncAccount(saved);
    }

    private String exchangeTikTok(String code) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("client_key", tiktokClientKey)
                    .add("client_secret", tiktokClientSecret)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", callbackUri(SocialAccount.Platform.TIKTOK))
                    .build();
            JsonNode root = postJson("https://open.tiktokapis.com/v2/oauth/token/", body);
            return root.path("data").path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("TikTok token exchange failed: " + e.getMessage(), e);
        }
    }

    private String exchangeThreads(String code) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("client_id", threadsClientId)
                    .add("client_secret", threadsClientSecret)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", callbackUri(SocialAccount.Platform.THREADS))
                    .add("code", code)
                    .build();
            String shortToken = postForm("https://graph.threads.net/oauth/access_token", body, "access_token");

            // Меняем на long-lived (60 дней)
            String longTokenUrl = "https://graph.threads.net/access_token"
                    + "?grant_type=th_exchange_token"
                    + "&client_secret=" + threadsClientSecret
                    + "&access_token=" + shortToken;
            return getField(longTokenUrl, "access_token");
        } catch (Exception e) {
            throw new RuntimeException("Threads token exchange failed: " + e.getMessage(), e);
        }
    }

    // ─── Поиск/создание SocialAccount ────────────────────────────────────────

    private SocialAccount findOrCreateAccount(SocialAccount.Platform platform, UUID userId) {
        // Ищем у блогера
        Optional<SocialAccount> existing = socialAccountRepository
                .findByBloggerProfileUserIdAndPlatform(userId, platform);
        if (existing.isPresent()) return existing.get();

        // Ищем у бренда
        existing = socialAccountRepository
                .findByBrandProfileUserIdAndPlatform(userId, platform);
        if (existing.isPresent()) return existing.get();

        // Создаём новый — определяем к какому профилю привязать
        Optional<BloggerProfile> blogger = bloggerProfileRepository.findByUserId(userId);
        if (blogger.isPresent()) {
            return SocialAccount.builder()
                    .bloggerProfile(blogger.get())
                    .platform(platform)
                    .build();
        }
        Optional<BrandProfile> brand = brandProfileRepository.findByUserId(userId);
        if (brand.isPresent()) {
            return SocialAccount.builder()
                    .brandProfile(brand.get())
                    .platform(platform)
                    .build();
        }
        throw new RuntimeException("No profile found for user: " + userId);
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private String postForm(String url, RequestBody body, String field) throws Exception {
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) throw new RuntimeException("Empty response from " + url);
            JsonNode root = objectMapper.readTree(response.body().string());
            if (root.has("error_type") || root.has("error"))
                throw new RuntimeException(root.path("error_message").asText(root.path("error").asText()));
            return root.path(field).asText();
        }
    }

    private JsonNode postJson(String url, RequestBody body) throws Exception {
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) throw new RuntimeException("Empty response from " + url);
            return objectMapper.readTree(response.body().string());
        }
    }

    private String getField(String url, String field) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) throw new RuntimeException("Empty response from " + url);
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path(field).asText();
        }
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    private String encodeState(UUID userId, SocialAccount.Platform platform) {
        return Base64.getUrlEncoder().encodeToString(
                (userId.toString() + "|" + platform.name()).getBytes());
    }

    private UUID decodeUserId(String state) {
        String decoded = new String(Base64.getUrlDecoder().decode(state));
        return UUID.fromString(decoded.split("\\|")[0]);
    }

    private String callbackUri(SocialAccount.Platform platform) {
        return backendUrl + "/api/v1/social/oauth/" + platform.name().toLowerCase() + "/callback";
    }
}