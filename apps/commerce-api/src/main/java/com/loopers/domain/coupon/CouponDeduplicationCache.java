package com.loopers.domain.coupon;

import java.time.Duration;

/**
 * 쿠폰 발급 중복 방지 캐시 추상화.
 *
 * <p>Application 레이어가 Redis SETNX/DEL 구현에 직접 의존하지 않도록
 * 도메인 레이어에 인터페이스를 정의한다.</p>
 */
public interface CouponDeduplicationCache {

    /**
     * 중복 발급을 방지하기 위해 키를 설정한다 (존재하지 않을 때만).
     *
     * @param userId   사용자 ID
     * @param couponId 쿠폰 ID
     * @param ttl      키 만료 시간
     * @return 최초 설정 성공 시 true, 이미 존재하면 false
     */
    boolean trySetIfAbsent(Long userId, Long couponId, Duration ttl);

    /**
     * 중복 방지 키를 삭제한다 (발급 실패 시 복원용).
     *
     * @param userId   사용자 ID
     * @param couponId 쿠폰 ID
     */
    void delete(Long userId, Long couponId);
}
