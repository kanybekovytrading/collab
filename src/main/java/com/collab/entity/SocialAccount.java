package com.collab.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SocialAccount extends BaseEntity {

    public enum Platform {
        INSTAGRAM, TIKTOK, YOUTUBE, TELEGRAM, VKONTAKTE, ODNOKLASSNIKI, WHATSAPP_BUSINESS, THREADS
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blogger_profile_id")
    private BloggerProfile bloggerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_profile_id")
    private BrandProfile brandProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    private String username;
    private String url;

    @Column(name = "followers_count")
    @Builder.Default
    private Long followersCount = 0L;

    /** true — аккаунт найден и верифицирован через API */
    @Builder.Default
    private boolean verified = false;

    /** Когда последний раз синхронизировали подписчиков */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /**
     * OAuth access token для платформ, требующих авторизации
     * (Instagram, TikTok, Threads). Хранится в зашифрованном виде.
     */
    @Column(name = "access_token", length = 2048)
    private String accessToken;
}
