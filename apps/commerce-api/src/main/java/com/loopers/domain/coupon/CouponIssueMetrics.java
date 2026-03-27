package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 메트릭 추상화.
 *
 * <p>Application 레이어가 Infrastructure의 Micrometer 구현에 의존하지 않도록
 * 도메인 레이어에 인터페이스를 정의한다.</p>
 */
public interface CouponIssueMetrics {

    /**
     * Redis DECR 장애 fallback 발생을 기록한다.
     */
    void incrementRedisFallback();

    /**
     * Redis INCR 복원 실패를 기록한다.
     */
    void incrementIncrRestoreFail();
}
