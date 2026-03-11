package com.collab.service;

import com.collab.common.enums.UserRole;
import com.collab.dto.AuthDto;
import com.collab.entity.*;
import com.collab.repository.UserRepository;
import com.collab.security.JwtService;
import com.collab.service.SocialOAuthService.InstagramProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SocialOAuthService socialOAuthService;
    private final MinioService minioService;

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already in use");
        if (req.getPhone() != null && userRepository.existsByPhone(req.getPhone()))
            throw new IllegalArgumentException("Phone already in use");

        User user = User.builder()
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .currentRole(req.getRole())
                .roles(Set.of(req.getRole()))
                .build();

        if (req.getRole() == UserRole.BLOGGER || req.getRole() == UserRole.AI_CREATOR) {
            BloggerProfile profile = BloggerProfile.builder().user(user).build();
            user.setBloggerProfile(profile);
        } else if (req.getRole() == UserRole.BRAND) {
            BrandProfile profile = BrandProfile.builder().user(user).build();
            user.setBrandProfile(profile);
        }

        userRepository.save(user);
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid credentials");
        if (!user.isActive())
            throw new IllegalArgumentException("Account is deactivated");
        return buildResponse(user);
    }

    @Transactional
    public AuthDto.AuthResponse loginWithOAuth(AuthDto.OAuthRequest req) {
        FirebaseToken firebaseToken;
        try {
            firebaseToken = FirebaseAuth.getInstance().verifyIdToken(req.getFirebaseToken());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid OAuth token: " + e.getMessage());
        }

        String email = firebaseToken.getEmail();
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email not provided by OAuth provider");

        String name = firebaseToken.getName() != null ? firebaseToken.getName() : email;
        String picture = (String) firebaseToken.getClaims().get("picture");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .fullName(name)
                    .email(email)
                    .avatarUrl(picture)
                    .currentRole(req.getRole())
                    .roles(Set.of(req.getRole()))
                    .verified(true)
                    .build();

            if (req.getRole() == UserRole.BLOGGER || req.getRole() == UserRole.AI_CREATOR) {
                BloggerProfile profile = BloggerProfile.builder().user(newUser).build();
                newUser.setBloggerProfile(profile);
            } else if (req.getRole() == UserRole.BRAND) {
                BrandProfile profile = BrandProfile.builder().user(newUser).build();
                newUser.setBrandProfile(profile);
            }

            return userRepository.save(newUser);
        });

        if (!user.isActive())
            throw new IllegalArgumentException("Account is deactivated");

        return buildResponse(user);
    }

    @Transactional
    public AuthDto.AuthResponse loginWithInstagram(AuthDto.InstagramLoginRequest req) {
        // 1. Обменять code на access token
        String accessToken = socialOAuthService.exchangeInstagramWithRedirect(
                req.getCode(), req.getRedirectUri());

        // 2. Получить профиль Instagram
        InstagramProfile profile = socialOAuthService.fetchInstagramProfile(accessToken);

        // 3. Найти пользователя по Instagram ID или создать нового
        User user = userRepository.findByOauthProviderId(profile.id()).orElseGet(() -> {
            // Синтетический email — Instagram не даёт email в Basic Display API
            String syntheticEmail = "ig_" + profile.id() + "@instagram.auth";

            // Если вдруг email уже занят (очень редкий edge case) — пользователь уже есть
            if (userRepository.existsByEmail(syntheticEmail)) {
                return userRepository.findByEmail(syntheticEmail)
                        .orElseThrow(() -> new RuntimeException("User lookup error"));
            }

            User newUser = User.builder()
                    .fullName(profile.username())
                    .email(syntheticEmail)
                    .oauthProviderId(profile.id())
                    .oauthProvider("instagram")
                    .currentRole(req.getRole())
                    .roles(Set.of(req.getRole()))
                    .verified(true)
                    .build();

            if (req.getRole() == UserRole.BLOGGER || req.getRole() == UserRole.AI_CREATOR) {
                BloggerProfile bloggerProfile = BloggerProfile.builder().user(newUser).build();
                newUser.setBloggerProfile(bloggerProfile);
            } else if (req.getRole() == UserRole.BRAND) {
                BrandProfile brandProfile = BrandProfile.builder().user(newUser).build();
                newUser.setBrandProfile(brandProfile);
            }

            return userRepository.save(newUser);
        });

        if (!user.isActive())
            throw new IllegalArgumentException("Account is deactivated");

        // 4. Сохранить/обновить access token в SocialAccount
        socialOAuthService.saveInstagramToken(user, accessToken, profile.username());

        return buildResponse(user);
    }

    public AuthDto.AuthResponse refresh(AuthDto.RefreshRequest req) {
        if (!jwtService.isValid(req.getRefreshToken()))
            throw new IllegalArgumentException("Invalid refresh token");
        var userId = jwtService.extractUserId(req.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return buildResponse(user);
    }

    private AuthDto.AuthResponse buildResponse(User user) {
        AuthDto.UserInfo info = new AuthDto.UserInfo();
        info.setId(user.getId().toString());
        info.setFullName(user.getFullName());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setCurrentRole(user.getCurrentRole().name());
        info.setAvatarUrl(minioService.resolveUrl(user.getAvatarUrl()));
        info.setVerified(user.isVerified());

        AuthDto.AuthResponse resp = new AuthDto.AuthResponse();
        resp.setAccessToken(jwtService.generateAccessToken(user.getId(), user.getEmail()));
        resp.setRefreshToken(jwtService.generateRefreshToken(user.getId()));
        resp.setUser(info);
        return resp;
    }
}
