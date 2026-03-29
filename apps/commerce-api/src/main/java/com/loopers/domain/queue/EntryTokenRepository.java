package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {

    void issue(Long memberId, String token, int ttlSeconds);

    Optional<String> findToken(Long memberId);

    void consume(Long memberId);
}
