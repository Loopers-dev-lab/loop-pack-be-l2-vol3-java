package com.loopers.domain.coupon;

public interface UserCouponRepository {
    boolean existsByCouponIdAndMemberId(Long couponId, Long memberId);
    UserCouponModel save(UserCouponModel model);
}
