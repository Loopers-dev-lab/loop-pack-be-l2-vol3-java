package com.loopers.domain.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEntryTokenRepository implements EntryTokenRepository {

    private final Map<Long, TokenEntry> store = new ConcurrentHashMap<>();

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

    private record TokenEntry(String token, Instant expiresAt) {
    }
}
