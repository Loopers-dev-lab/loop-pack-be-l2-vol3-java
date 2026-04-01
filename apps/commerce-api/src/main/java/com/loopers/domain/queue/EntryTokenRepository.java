package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

public interface EntryTokenRepository {
    /** 입장 토큰을 발급한다. (TTL 적용) */
    void issueToken(Long userId, String token, Duration ttl);

    /** 유저의 입장 토큰을 조회한다. */
    Optional<String> getToken(Long userId);

    /** 입장 토큰을 삭제한다. (사용 완료) */
    void deleteToken(Long userId);

    /** 토큰이 유효한지 검증한다. */
    boolean validateToken(Long userId, String token);
}
