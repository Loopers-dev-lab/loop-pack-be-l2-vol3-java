package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

public interface EntryTokenRepository {
    /** 입장 토큰을 발급한다. (TTL 적용) */
    void issueToken(Long userId, String token, Duration ttl);

    /** 유저의 입장 토큰을 조회한다. */
    Optional<String> getToken(Long userId);

    /** 토큰이 일치하면 원자적으로 삭제하고 true를 반환한다. */
    boolean consumeIfMatch(Long userId, String token);

    /** 주문 실패 시 소비된 토큰을 복원한다. */
    void restoreToken(Long userId, String token);
}
