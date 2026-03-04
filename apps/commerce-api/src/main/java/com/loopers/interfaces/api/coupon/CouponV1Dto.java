package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;

import java.time.LocalDateTime;

public class CouponV1Dto {

    // Response

    public record IssuedCouponResponse(
            Long id,
            Long couponId,
            String couponName,
            String type,
            int value,
            String status,
            LocalDateTime expiredAt,
            LocalDateTime createdAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.id(),
                    info.couponId(),
                    info.couponName(),
                    info.type().name(),
                    info.value(),
                    info.status(),
                    info.expiredAt(),
                    info.createdAt()
            );
        }
    }
}
