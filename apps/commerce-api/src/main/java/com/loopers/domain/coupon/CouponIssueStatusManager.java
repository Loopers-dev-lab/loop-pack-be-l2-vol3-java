package com.loopers.domain.coupon;

import java.util.Optional;

/**
 * 쿠폰 발급 상태 관리를 위한 도메인 포트.
 *
 * <p>비동기 쿠폰 발급 요청의 처리 상태(PENDING/COMPLETED/FAILED)를 추적한다.
 * 인프라스트럭처 계층에서 구체적인 저장소(Redis 등)로 구현한다.</p>
 */
public interface CouponIssueStatusManager {

    /**
     * 발급 상태를 PENDING으로 저장한다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     */
    void markPending(Long couponId, Long userId);

    /**
     * 발급 상태를 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 발급 상태 (PENDING/COMPLETED/FAILED), 존재하지 않으면 empty
     */
    Optional<String> getStatus(Long couponId, Long userId);
}
