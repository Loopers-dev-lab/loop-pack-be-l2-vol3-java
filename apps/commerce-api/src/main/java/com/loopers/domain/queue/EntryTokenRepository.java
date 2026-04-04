package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface EntryTokenRepository {

    void save(EntryToken token, long ttlSeconds);

    Optional<EntryToken> findByUserId(Long userId);

    Optional<EntryToken> findAndDeleteByUserId(Long userId);

    Optional<EntryTokenConsumeResult> consumeIfActivated(Long userId, long currentTimeMillis);

    void restore(EntryToken token, long ttlSeconds, long statusTtlSeconds);

    void saveStatus(Long userId, String status, long ttlSeconds);

    Optional<String> getStatus(Long userId);

    long countActiveTokens();

    Optional<String> acquireLock(String lockKey, long ttlMillis);

    void releaseLock(String lockKey, String lockValue);

    void saveStagingBatch(List<Long> userIds);

    List<Long> getStagingBatch();

    void deleteStagingBatch();
}
