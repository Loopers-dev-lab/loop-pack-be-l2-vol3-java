package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.dto.FindIssuedCouponResDto;

import java.time.LocalDateTime;

public record FindIssuedCouponApiResDto(
        Long userCouponId,
        Long memberId,
        String status,
        LocalDateTime usedAt
) {
    public static FindIssuedCouponApiResDto from(FindIssuedCouponResDto dto) {
        return new FindIssuedCouponApiResDto(
                dto.userCouponId(),
                dto.memberId(),
                dto.status(),
                dto.usedAt()
        );
    }
}
