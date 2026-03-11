package com.collab.service.social;

import com.collab.entity.SocialAccount;

import java.util.Optional;

public interface SocialPlatformClient {

    SocialAccount.Platform platform();

    /**
     * Получить кол-во подписчиков по username (для публичных API без OAuth).
     * Возвращает empty если аккаунт не найден или API недоступен.
     */
    Optional<Long> fetchByUsername(String username);

    /**
     * Получить кол-во подписчиков по access token (для OAuth платформ).
     * Возвращает empty если токен невалиден или API недоступен.
     */
    default Optional<Long> fetchByToken(String accessToken) {
        return Optional.empty();
    }

    /** Платформа требует OAuth для получения данных */
    default boolean requiresOAuth() {
        return false;
    }
}