package com.loopers.domain.coupon;

public interface UserCouponRepository {

    void save(UserCoupon userCoupon);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
