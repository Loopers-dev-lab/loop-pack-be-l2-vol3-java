package com.loopers.domain.user;

import java.time.Duration;

/**
 * 인증 캐시 저장소 인터페이스.
 *
 * <p>인증 결과를 캐시하여 Polling 인증 DB 부하를 줄이기 위한 도메인 계약.
 * infrastructure 계층에서 Redis 등으로 구현한다.</p>
 *
 * @see AuthCacheService
 */
public interface AuthCacheRepository {

    /**
     * 캐시된 인증 정보를 조회한다.
     *
     * @param key 캐시 키 (loginId + passwordHash 기반)
     * @return 캐시된 JSON 문자열, 미스 시 null
     */
    String get(String key);

    /**
     * 인증 정보를 캐시에 저장한다.
     *
     * @param key  캐시 키
     * @param json 저장할 JSON 문자열
     * @param ttl  캐시 유효 기간
     */
    void set(String key, String json, Duration ttl);
}
