package com.collab.entity;

import com.collab.common.enums.Gender;
import com.collab.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(unique = true)
    private String phone;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private UserRole currentRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<UserRole> roles = new HashSet<>();

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String city;
    private String country;
    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Builder.Default
    private boolean verified = false;

    @Builder.Default
    private boolean active = true;

    @Column(name = "fcm_token")
    private String fcmToken; // Firebase Cloud Messaging device token

    /** ID пользователя у OAuth провайдера (Instagram ID, TikTok ID и т.д.) */
    @Column(name = "oauth_provider_id", unique = true)
    private String oauthProviderId;

    /** Название OAuth провайдера: "instagram", "tiktok", "threads" */
    @Column(name = "oauth_provider")
    private String oauthProvider;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BloggerProfile bloggerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BrandProfile brandProfile;
}
