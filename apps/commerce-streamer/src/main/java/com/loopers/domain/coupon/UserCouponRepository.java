package com.loopers.domain.coupon;

public interface UserCouponRepository {
    boolean existsByRefMemberIdAndRefCouponTemplateId(Long refMemberId, Long refCouponTemplateId);
    UserCouponModel save(UserCouponModel model);
}
