package com.loopers.domain.queue.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.domain.queue.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class EntryTokenService {

    private final QueueRepository queueRepository;

    private static final long TOKEN_TTL_SECONDS = 300;
    private static final long JITTER_MAX_MS = 2000;

    private final Cache<Long, TokenInfo> localTokenCache = Caffeine.newBuilder()
            .expireAfterWrite(TOKEN_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    public record TokenInfo(String token, long availableAtMillis) {}

    public String issueToken(Long memberId) {
        String token = UUID.randomUUID().toString();
        long delayMs = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_MS + 1);
        long availableAt = System.currentTimeMillis() + delayMs;

        queueRepository.setToken(memberId, token, TOKEN_TTL_SECONDS);
        localTokenCache.put(memberId, new TokenInfo(token, availableAt));
        return token;
    }

    public boolean validateToken(Long memberId) {
        try {
            return queueRepository.getToken(memberId) != null;
        } catch (Exception e) {
            TokenInfo info = localTokenCache.getIfPresent(memberId);
            return info != null;
        }
    }

    public String getToken(Long memberId) {
        try {
            return queueRepository.getToken(memberId);
        } catch (Exception e) {
            TokenInfo info = localTokenCache.getIfPresent(memberId);
            return info != null ? info.token() : null;
        }
    }

    public void deleteToken(Long memberId) {
        queueRepository.deleteToken(memberId);
        localTokenCache.invalidate(memberId);
    }

    public void refreshTtl(Long memberId) {
        queueRepository.refreshTokenTtl(memberId, TOKEN_TTL_SECONDS);
    }

    public long getRemainingDelayMs(Long memberId) {
        TokenInfo info = localTokenCache.getIfPresent(memberId);
        if (info == null) return 0;
        return Math.max(0, info.availableAtMillis() - System.currentTimeMillis());
    }
}
