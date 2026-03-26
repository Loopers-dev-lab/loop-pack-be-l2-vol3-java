package com.loopers.domain.coupon;

public interface UserCouponRepository {

    void save(UserCoupon userCoupon);

    long countByCouponTemplateId(Long couponTemplateId);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
