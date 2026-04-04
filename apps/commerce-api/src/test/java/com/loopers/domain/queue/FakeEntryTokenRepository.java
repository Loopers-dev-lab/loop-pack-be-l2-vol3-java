package com.loopers.domain.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class FakeEntryTokenRepository implements EntryTokenRepository {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private final Map<Long, EntryToken> tokenStore = new HashMap<>();
    private final Map<Long, Long> tokenTtlStore = new HashMap<>();
    private final Map<Long, String> statusStore = new HashMap<>();
    private final Map<String, String> lockStore = new HashMap<>();
    private List<Long> stagingBatch = new ArrayList<>();

    @Override
    public void save(EntryToken token, long ttlSeconds) {
        tokenStore.put(token.userId(), token);
        tokenTtlStore.put(token.userId(), ttlSeconds);
    }

    @Override
    public Optional<EntryToken> findByUserId(Long userId) {
        return Optional.ofNullable(tokenStore.get(userId));
    }

    @Override
    public Optional<EntryToken> findAndDeleteByUserId(Long userId) {
        statusStore.remove(userId);
        tokenTtlStore.remove(userId);
        return Optional.ofNullable(tokenStore.remove(userId));
    }

    @Override
    public Optional<EntryTokenConsumeResult> consumeIfActivated(Long userId, long currentTimeMillis) {
        EntryToken token = tokenStore.get(userId);
        if (token == null) {
            return Optional.empty();
        }
        if (currentTimeMillis >= token.activateAt()) {
            tokenStore.remove(userId);
            tokenTtlStore.remove(userId);
            statusStore.remove(userId);
            return Optional.of(new EntryTokenConsumeResult(token, true));
        }
        return Optional.of(new EntryTokenConsumeResult(token, false));
    }

    @Override
    public void restore(EntryToken token, long ttlSeconds, long statusTtlSeconds) {
        tokenStore.put(token.userId(), token);
        tokenTtlStore.put(token.userId(), ttlSeconds);
        statusStore.put(token.userId(), "TOKEN_ISSUED");
    }

    @Override
    public void saveStatus(Long userId, String status, long ttlSeconds) {
        statusStore.put(userId, status);
    }

    @Override
    public Optional<String> getStatus(Long userId) {
        return Optional.ofNullable(statusStore.get(userId));
    }

    @Override
    public long countActiveTokens() {
        return tokenStore.size();
    }

    @Override
    public Optional<String> acquireLock(String lockKey, long ttlMillis) {
        String value = UUID.randomUUID().toString();
        if (lockStore.putIfAbsent(lockKey, value) == null) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    @Override
    public void releaseLock(String lockKey, String lockValue) {
        lockStore.computeIfPresent(lockKey, (k, v) -> v.equals(lockValue) ? null : v);
    }

    @Override
    public void saveStagingBatch(List<Long> userIds) {
        stagingBatch = new ArrayList<>(userIds);
    }

    @Override
    public List<Long> getStagingBatch() {
        return List.copyOf(stagingBatch);
    }

    @Override
    public void deleteStagingBatch() {
        stagingBatch.clear();
    }

    public void clearAll() {
        tokenStore.clear();
        tokenTtlStore.clear();
        statusStore.clear();
        lockStore.clear();
        stagingBatch.clear();
    }
}
