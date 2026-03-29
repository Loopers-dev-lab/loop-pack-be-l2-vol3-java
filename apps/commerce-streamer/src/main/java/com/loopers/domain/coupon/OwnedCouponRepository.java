package com.loopers.domain.coupon;

/**
 * 보유 쿠폰 도메인 리포지토리 인터페이스.
 */
public interface OwnedCouponRepository {

    /**
     * 보유 쿠폰을 저장한다.
     *
     * @param ownedCoupon 저장할 보유 쿠폰
     * @return 저장된 보유 쿠폰
     */
    OwnedCoupon save(OwnedCoupon ownedCoupon);

    /**
     * 해당 쿠폰이 사용자에게 이미 발급되었는지 확인한다.
     *
     * @param coupon 쿠폰
     * @param userId 사용자 ID
     * @return 이미 발급되었으면 true
     */
    boolean existsByCouponAndUserId(Coupon coupon, Long userId);
}
