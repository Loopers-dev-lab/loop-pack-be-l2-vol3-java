package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;

import java.time.ZonedDateTime;

/**
 * 대고객 쿠폰 API 요청/응답 DTO.
 */
public class CouponV1Dto {

    /** 발급 쿠폰 응답 (내 쿠폰 목록·발급 결과) */
    public record IssuedCouponResponse(
        Long id,
        Long couponId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            if (info == null) {
                return null;
            }
            return new IssuedCouponResponse(
                info.id(),
                info.couponId(),
                info.status(),
                info.expiredAt(),
                info.usedAt(),
                info.createdAt()
            );
        }
    }
}
