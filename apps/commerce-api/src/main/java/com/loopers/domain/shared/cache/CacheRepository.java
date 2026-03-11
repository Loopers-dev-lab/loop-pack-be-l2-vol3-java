package com.loopers.domain.shared.cache;

import java.time.Duration;

/**
 * 키-값 기반 캐시 저장소 인터페이스.
 *
 * <p>특정 캐시 구현(Redis, Caffeine 등)에 의존하지 않는 범용 인터페이스로,
 * 타입 정보는 메서드 호출 시 {@link CacheType}을 통해 전달한다.</p>
 *
 * @see CacheType
 */
public interface CacheRepository {

    /**
     * 캐시에 값을 저장한다. TTL 없이 저장되며, 명시적으로 삭제하기 전까지 유지된다.
     *
     * @param key   캐시 키
     * @param value 저장할 값
     */
    <T> void put(String key, T value);

    /**
     * 캐시에 값을 저장하며, 지정한 TTL이 만료되면 자동으로 삭제된다.
     *
     * @param key   캐시 키
     * @param value 저장할 값
     * @param ttl   만료 시간
     */
    <T> void put(String key, T value, Duration ttl);

    /**
     * 캐시에서 값을 조회한다.
     *
     * @param key  캐시 키
     * @param type 역직렬화 대상 타입 토큰
     * @return 캐시된 값, 캐시 미스 또는 역직렬화 실패 시 {@code null}
     */
    <T> T get(String key, CacheType<T> type);

    /**
     * 패턴에 매칭되는 캐시 키를 일괄 삭제한다.
     *
     * @param keyPattern 삭제할 키 패턴 (예: {@code "product:list:*"})
     */
    void evict(String keyPattern);
}
