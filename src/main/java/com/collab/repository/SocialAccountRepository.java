package com.collab.repository;

import com.collab.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    /** Все аккаунты которые нужно синхронизировать (есть username или access token) */
    @Query("SELECT s FROM SocialAccount s WHERE s.username IS NOT NULL OR s.accessToken IS NOT NULL")
    List<SocialAccount> findAllSyncable();

    Optional<SocialAccount> findByBloggerProfileUserIdAndPlatform(UUID userId, SocialAccount.Platform platform);

    Optional<SocialAccount> findByBrandProfileUserIdAndPlatform(UUID userId, SocialAccount.Platform platform);
}