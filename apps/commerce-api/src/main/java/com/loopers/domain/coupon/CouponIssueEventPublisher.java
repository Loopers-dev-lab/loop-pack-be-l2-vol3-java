package com.loopers.domain.coupon;

/**
 * 쿠폰 발급 이벤트 발행을 위한 도메인 포트.
 *
 * <p>Redis 검증 성공 후 비동기 처리를 위해 이벤트를 발행한다.
 * 인프라스트럭처 계층에서 구체적인 메시지 브로커(Kafka 등)로 구현한다.</p>
 */
public interface CouponIssueEventPublisher {

    /**
     * 쿠폰 발급 이벤트를 발행한다.
     *
     * @param couponId 발급된 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     */
    void publishEvent(Long couponId, Long userId);
}
