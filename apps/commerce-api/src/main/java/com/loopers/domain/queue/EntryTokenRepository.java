package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {

    void saveEntryToken(Long userId, String token, long ttlSeconds);

    Optional<String> findEntryToken(Long userId);

    void deleteEntryToken(Long userId);
}

