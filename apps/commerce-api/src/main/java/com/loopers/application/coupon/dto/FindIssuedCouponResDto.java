package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.model.UserCoupon;

import java.time.LocalDateTime;

public record FindIssuedCouponResDto(
        Long userCouponId,
        Long memberId,
        String status,
        LocalDateTime usedAt
) {
    public static FindIssuedCouponResDto from(UserCoupon userCoupon) {
        return new FindIssuedCouponResDto(
                userCoupon.getId(),
                userCoupon.getMemberId(),
                userCoupon.getStatus().name(),
                userCoupon.getUsedAt()
        );
    }
}
