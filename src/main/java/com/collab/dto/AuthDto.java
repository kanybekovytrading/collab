package com.collab.dto;

import com.collab.common.enums.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank private String fullName;
        private String phone;
        @NotBlank @Email private String email;
        @NotBlank @Size(min = 8) private String password;
        @NotNull private UserRole role;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email private String email;
        @NotBlank private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank private String refreshToken;
    }

    @Data
    public static class OAuthRequest {
        /** Firebase ID token полученный от Google Sign-In или Sign in with Apple */
        @NotBlank private String firebaseToken;
        /** Роль нового пользователя (только при первом входе) */
        @NotNull private UserRole role;
    }

    @Data
    public static class InstagramLoginRequest {
        /**
         * Authorization code полученный после редиректа от Instagram.
         * Мобильное приложение перехватывает его из redirect_uri.
         */
        @NotBlank private String code;
        /**
         * redirect_uri который использовался при генерации authUrl.
         * Должен точно совпадать с тем что зарегистрирован в Facebook App.
         */
        @NotBlank private String redirectUri;
        /** Роль нового пользователя (только при первом входе) */
        @NotNull private UserRole role;
    }

    @Data
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private UserInfo user;
    }

    @Data
    public static class UserInfo {
        private String id;
        private String fullName;
        private String email;
        private String phone;
        private String currentRole;
        private String avatarUrl;
        private boolean verified;
    }
}
