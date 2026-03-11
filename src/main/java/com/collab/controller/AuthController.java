package com.collab.controller;

import com.collab.common.dto.ApiResponse1;
import com.collab.dto.AuthDto;
import com.collab.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход, обновление токена")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
        summary = "Регистрация нового пользователя",
        description = """
            Создаёт учётную запись. Роль определяет тип профиля:
            - `BLOGGER` / `AI_CREATOR` → создаётся BloggerProfile
            - `BRAND` → создаётся BrandProfile
            
            Возвращает пару JWT токенов и данные пользователя.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешная регистрация"),
        @ApiResponse(responseCode = "400", description = "Email уже занят или невалидные данные")
    })
    public ResponseEntity<ApiResponse1<AuthDto.AuthResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(authService.register(req)));
    }

    @PostMapping("/login")
    @Operation(
        summary = "Вход в систему",
        description = "Возвращает `accessToken` (24ч) и `refreshToken` (7 дней)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешный вход"),
        @ApiResponse(responseCode = "400", description = "Неверный email или пароль")
    })
    public ResponseEntity<ApiResponse1<AuthDto.AuthResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(authService.login(req)));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Обновить access токен",
        description = "Передайте `refreshToken` — получите новую пару токенов."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Токен обновлён"),
        @ApiResponse(responseCode = "400", description = "Refresh токен невалиден или истёк")
    })
    public ResponseEntity<ApiResponse1<AuthDto.AuthResponse>> refresh(
            @Valid @RequestBody AuthDto.RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(authService.refresh(req)));
    }

    @PostMapping("/instagram")
    @Operation(
        summary = "Вход / регистрация через Instagram",
        description = """
            **Мобильный флоу:**
            1. Вызови `GET /api/v1/social/oauth/INSTAGRAM/connect` → получи `authUrl`
            2. Открой WebView/Browser с `authUrl`
            3. Пользователь разрешает доступ → Instagram редиректит на `redirect_uri?code=...`
            4. Приложение перехватывает `code` из redirect URI
            5. Отправь `POST /api/v1/auth/instagram` с `code`, `redirectUri` и `role`

            При первом входе создаётся аккаунт с указанной `role`.
            При повторном — выполняется вход в существующий.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешный вход / регистрация"),
        @ApiResponse(responseCode = "400", description = "Невалидный code или Instagram недоступен")
    })
    public ResponseEntity<ApiResponse1<AuthDto.AuthResponse>> instagramLogin(
            @Valid @RequestBody AuthDto.InstagramLoginRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(authService.loginWithInstagram(req)));
    }

    @PostMapping("/oauth")
    @Operation(
        summary = "Вход / регистрация через Google или Apple ID",
        description = """
            Принимает **Firebase ID token**, полученный от:
            - Google Sign-In (через Firebase SDK)
            - Sign in with Apple (через Firebase SDK)

            Если пользователь с таким email уже существует — выполняется вход.
            Если нет — создаётся новый аккаунт с указанной ролью (`role`).

            Возвращает пару JWT токенов и данные пользователя.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешный вход / регистрация"),
        @ApiResponse(responseCode = "400", description = "Невалидный Firebase токен или email не предоставлен провайдером")
    })
    public ResponseEntity<ApiResponse1<AuthDto.AuthResponse>> oauth(
            @Valid @RequestBody AuthDto.OAuthRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(authService.loginWithOAuth(req)));
    }
}
