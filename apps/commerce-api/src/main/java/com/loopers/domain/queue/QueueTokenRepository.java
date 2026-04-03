package com.loopers.domain.queue;

import java.util.Optional;

public interface QueueTokenRepository {

    void issueToken(String eventId, Long userId, String token, long ttlSeconds);

    Optional<String> getToken(String eventId, Long userId);

    long getTokenTtl(String eventId, Long userId);

    void removeToken(String eventId, Long userId);
}
