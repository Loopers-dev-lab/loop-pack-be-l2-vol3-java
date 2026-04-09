package com.loopers.domain.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEntryTokenRepository implements EntryTokenRepository {

    private final Map<Long, TokenEntry> store = new ConcurrentHashMap<>();
    private final Map<String, String> locks = new ConcurrentHashMap<>();

    @Override
    public void issueToken(Long userId, String token, Duration ttl) {
        store.put(userId, new TokenEntry(token, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> getToken(Long userId) {
        TokenEntry entry = store.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(userId);
            return Optional.empty();
        }
        return Optional.of(entry.token());
    }

    @Override
    public synchronized boolean consumeIfMatch(Long userId, String token) {
        return getToken(userId)
                .filter(stored -> stored.equals(token))
                .map(stored -> {
                    store.remove(userId);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void restoreToken(Long userId, String token) {
        store.put(userId, new TokenEntry(token, Instant.now().plus(Duration.ofMinutes(5))));
    }

    @Override
    public long countActiveTokens() {
        // 만료된 토큰 제거 후 카운트
        store.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
        return store.size();
    }

    @Override
    public synchronized Optional<String> acquireLock(String key, long ttlMs) {
        if (locks.containsKey(key)) {
            return Optional.empty();
        }
        String value = UUID.randomUUID().toString();
        locks.put(key, value);
        return Optional.of(value);
    }

    @Override
    public synchronized void releaseLock(String key, String value) {
        if (value.equals(locks.get(key))) {
            locks.remove(key);
        }
    }

    private record TokenEntry(String token, Instant expiresAt) {
    }
}
