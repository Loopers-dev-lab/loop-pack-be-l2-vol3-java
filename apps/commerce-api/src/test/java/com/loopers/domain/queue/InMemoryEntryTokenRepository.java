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
    public void deleteToken(Long userId) {
        store.remove(userId);
    }

    @Override
    public boolean validateToken(Long userId, String token) {
        return getToken(userId)
                .map(stored -> stored.equals(token))
                .orElse(false);
    }

    private record TokenEntry(String token, Instant expiresAt) {
    }
}
