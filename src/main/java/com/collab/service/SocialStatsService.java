package com.collab.service;

import com.collab.entity.SocialAccount;
import com.collab.repository.SocialAccountRepository;
import com.collab.service.social.SocialPlatformClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SocialStatsService {

    private final SocialAccountRepository socialAccountRepository;
    private final Map<SocialAccount.Platform, SocialPlatformClient> clients;

    public SocialStatsService(SocialAccountRepository socialAccountRepository,
                               List<SocialPlatformClient> clientList) {
        this.socialAccountRepository = socialAccountRepository;
        this.clients = clientList.stream()
                .collect(Collectors.toMap(SocialPlatformClient::platform, Function.identity()));
        log.info("Social stats clients registered: {}", clients.keySet());
    }

    /**
     * Синхронизировать конкретный аккаунт сразу после добавления.
     * Запускается асинхронно, чтобы не блокировать ответ пользователю.
     */
    @Async
    @Transactional
    public void syncAccount(SocialAccount account) {
        Optional<Long> count = fetchCount(account);
        count.ifPresent(c -> {
            account.setFollowersCount(c);
            account.setVerified(true);
            account.setLastSyncedAt(LocalDateTime.now());
            socialAccountRepository.save(account);
            log.info("Synced {} @{}: {} followers",
                    account.getPlatform(), account.getUsername(), c);
        });
        if (count.isEmpty()) {
            log.warn("Could not fetch stats for {} @{}",
                    account.getPlatform(), account.getUsername());
        }
    }

    /**
     * Плановая синхронизация всех аккаунтов — каждые 24 часа.
     * Интервал можно изменить через app.social.sync-interval в application.yml (в миллисекундах).
     */
    @Scheduled(fixedDelayString = "${app.social.sync-interval:86400000}",
               initialDelayString = "${app.social.sync-initial-delay:60000}")
    @Transactional
    public void syncAll() {
        List<SocialAccount> accounts = socialAccountRepository.findAllSyncable();
        log.info("Starting scheduled sync for {} social accounts", accounts.size());
        int updated = 0;
        for (SocialAccount account : accounts) {
            Optional<Long> count = fetchCount(account);
            if (count.isPresent()) {
                account.setFollowersCount(count.get());
                account.setVerified(true);
                account.setLastSyncedAt(LocalDateTime.now());
                socialAccountRepository.save(account);
                updated++;
            }
        }
        log.info("Scheduled sync completed: {}/{} accounts updated", updated, accounts.size());
    }

    private Optional<Long> fetchCount(SocialAccount account) {
        SocialPlatformClient client = clients.get(account.getPlatform());
        if (client == null) {
            log.debug("No client registered for platform {}", account.getPlatform());
            return Optional.empty();
        }
        // OAuth платформы: используем access token если есть
        if (client.requiresOAuth()) {
            if (account.getAccessToken() == null) return Optional.empty();
            return client.fetchByToken(account.getAccessToken());
        }
        // Публичные платформы: используем username
        if (account.getUsername() == null) return Optional.empty();
        return client.fetchByUsername(account.getUsername());
    }
}