package com.collab.controller;

import com.collab.common.dto.ApiResponse1;
import com.collab.dto.SocialDto;
import com.collab.entity.SocialAccount;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.SocialOAuthService;
import com.collab.service.social.SocialPlatformClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/social")
@Tag(name = "Social", description = "Валидация и OAuth подключение соц. сетей")
public class SocialController {

    private final SocialOAuthService oAuthService;
    private final Map<SocialAccount.Platform, SocialPlatformClient> clients;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public SocialController(SocialOAuthService oAuthService,
                            List<SocialPlatformClient> clientList) {
        this.oAuthService = oAuthService;
        this.clients = clientList.stream()
                .collect(Collectors.toMap(SocialPlatformClient::platform, Function.identity()));
    }

    /**
     * Проверить аккаунт перед сохранением.
     * Для публичных платформ (YouTube, VK, Telegram) — сразу возвращает follower count.
     * Для OAuth платформ (Instagram, TikTok, Threads) — возвращает requiresOAuth=true.
     */
    @GetMapping("/validate")
    @Operation(
        summary = "Валидация аккаунта соц. сети",
        description = """
            Проверяет существование аккаунта по username.
            - YouTube, VK, Telegram — сразу возвращает кол-во подписчиков
            - Instagram, TikTok, Threads — возвращает `requiresOAuth: true` (нужна авторизация через OAuth)
            """
    )
    public ResponseEntity<ApiResponse1<SocialDto.ValidateResponse>> validate(
            @RequestParam SocialAccount.Platform platform,
            @RequestParam(required = false) String username) {

        SocialDto.ValidateResponse resp = new SocialDto.ValidateResponse();
        resp.setPlatform(platform);
        resp.setUsername(username);

        SocialPlatformClient client = clients.get(platform);
        if (client == null) {
            resp.setExists(false);
            return ResponseEntity.ok(ApiResponse1.ok(resp));
        }

        if (client.requiresOAuth()) {
            resp.setRequiresOAuth(true);
            resp.setExists(false);
            return ResponseEntity.ok(ApiResponse1.ok(resp));
        }

        if (username == null || username.isBlank()) {
            resp.setExists(false);
            return ResponseEntity.ok(ApiResponse1.ok(resp));
        }

        Optional<Long> count = client.fetchByUsername(username);
        resp.setExists(count.isPresent());
        count.ifPresent(resp::setFollowersCount);
        return ResponseEntity.ok(ApiResponse1.ok(resp));
    }

    /**
     * Получить OAuth URL для подключения платформы.
     * Фронт делает редирект на полученный authUrl.
     */
    @GetMapping("/oauth/{platform}/connect")
    @Operation(
        summary = "Получить OAuth URL для подключения соц. сети",
        description = "Возвращает URL на который нужно редиректнуть пользователя для авторизации."
    )
    public ResponseEntity<ApiResponse1<SocialDto.OAuthConnectResponse>> connect(
            @PathVariable SocialAccount.Platform platform,
            @CurrentUser User user) {

        String authUrl = oAuthService.buildAuthUrl(platform, user.getId());
        SocialDto.OAuthConnectResponse resp = new SocialDto.OAuthConnectResponse();
        resp.setPlatform(platform);
        resp.setAuthUrl(authUrl);
        return ResponseEntity.ok(ApiResponse1.ok(resp));
    }

    /**
     * OAuth callback — вызывается платформой после авторизации пользователя.
     * Обменивает code на token, сохраняет в БД, редиректит на фронт.
     */
    @GetMapping("/oauth/{platform}/callback")
    @Operation(summary = "OAuth callback (вызывается платформой автоматически)")
    public ResponseEntity<Void> callback(
            @PathVariable SocialAccount.Platform platform,
            @RequestParam String code,
            @RequestParam String state) {

        try {
            oAuthService.handleCallback(platform, code, state);
            // Редирект на фронт с успехом
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendUrl + "/profile?social_connected=" + platform.name().toLowerCase()));
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        } catch (Exception e) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendUrl + "/profile?social_error=" + platform.name().toLowerCase()));
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        }
    }
}