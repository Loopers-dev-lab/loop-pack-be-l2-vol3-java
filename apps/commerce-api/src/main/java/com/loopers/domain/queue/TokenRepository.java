package com.loopers.domain.queue;

public interface TokenRepository {

    void saveToken(Long userId, String token, long ttlSeconds);

    String getToken(Long userId);

    void deleteToken(Long userId);
}
