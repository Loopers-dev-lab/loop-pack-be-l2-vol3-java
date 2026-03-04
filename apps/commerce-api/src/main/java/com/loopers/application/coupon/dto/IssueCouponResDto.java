package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.model.UserCoupon;

public record IssueCouponResDto(
        Long userCouponId,
        Long couponTemplateId,
        String status
) {
    public static IssueCouponResDto from(UserCoupon userCoupon) {
        return new IssueCouponResDto(
                userCoupon.getId(),
                userCoupon.getCouponTemplateId(),
                userCoupon.getStatus().name()
        );
    }
}
