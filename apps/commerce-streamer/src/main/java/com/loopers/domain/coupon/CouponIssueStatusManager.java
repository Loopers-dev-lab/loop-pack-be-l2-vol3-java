package com.loopers.domain.coupon;

/**
 * 쿠폰 발급 상태 관리를 위한 도메인 포트.
 *
 * <p>Kafka Consumer에서 발급 처리 결과(성공/실패)를 반영한다.
 * 인프라스트럭처 계층에서 구체적인 저장소(Redis 등)로 구현한다.</p>
 */
public interface CouponIssueStatusManager {

    /**
     * 발급 상태를 COMPLETED로 변경한다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     */
    void markCompleted(Long couponId, Long userId);

    /**
     * 발급 상태를 FAILED로 변경한다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     */
    void markFailed(Long couponId, Long userId);
}
