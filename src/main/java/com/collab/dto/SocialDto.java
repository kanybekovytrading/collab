package com.collab.dto;

import com.collab.entity.SocialAccount;
import lombok.Data;

public class SocialDto {

    @Data
    public static class ValidateResponse {
        private SocialAccount.Platform platform;
        private String username;
        private boolean exists;
        private Long followersCount;
        private boolean requiresOAuth;
    }

    @Data
    public static class OAuthConnectResponse {
        /** URL на который нужно редиректнуть пользователя */
        private String authUrl;
        private SocialAccount.Platform platform;
    }
}