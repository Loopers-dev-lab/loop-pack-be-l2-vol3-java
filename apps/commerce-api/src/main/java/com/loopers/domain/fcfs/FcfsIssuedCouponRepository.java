package com.loopers.domain.fcfs;

/**
 * 발급된 선착순 쿠폰 Repository 인터페이스.
 */
public interface FcfsIssuedCouponRepository {

    FcfsIssuedCoupon save(FcfsIssuedCoupon issuedCoupon);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);
}
