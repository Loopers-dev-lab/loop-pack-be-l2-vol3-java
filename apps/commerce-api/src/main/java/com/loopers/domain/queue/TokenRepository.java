package com.loopers.domain.queue;

import java.util.Optional;

public interface TokenRepository {
    void save(String userId, String token, long ttlSeconds);
    Optional<String> findToken(String userId);
    void delete(String userId);
}