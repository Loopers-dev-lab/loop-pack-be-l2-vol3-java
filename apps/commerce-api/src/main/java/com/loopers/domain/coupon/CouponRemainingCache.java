package com.loopers.domain.coupon;

/**
 * 쿠폰 잔여 수량 캐시 추상화.
 *
 * <p>Application/Batch 레이어가 Redis 구현에 직접 의존하지 않도록
 * 도메인 레이어에 인터페이스를 정의한다.</p>
 */
public interface CouponRemainingCache {

    /**
     * 잔여 수량을 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @return 잔여 수량 문자열 (없으면 null)
     */
    String getRemaining(Long couponId);

    /**
     * 잔여 수량을 설정한다.
     *
     * @param couponId 쿠폰 ID
     * @param count    잔여 수량 문자열
     */
    void setRemaining(Long couponId, String count);

    /**
     * 잔여 수량을 1 감소시키고 감소 후 값을 반환한다.
     *
     * @param couponId 쿠폰 ID
     * @return 감소 후 잔여 수량 (키가 없으면 null)
     */
    Long decrementAndGet(Long couponId);

    /**
     * 잔여 수량을 1 증가시킨다 (보상 복원용).
     *
     * @param couponId 쿠폰 ID
     */
    void increment(Long couponId);
}
