package com.loopers.interfaces.coupon.dto;

import com.loopers.application.coupon.dto.CouponResult;
import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    public record OwnedCouponResponse(
        Long ownedCouponId,
        Long couponId,
        String couponName,
        String discountType,
        long discountValue,
        Long minOrderAmount,
        String status,
        ZonedDateTime usedAt,
        ZonedDateTime expiredAt,
        ZonedDateTime issuedAt
    ) {
        public static OwnedCouponResponse from(CouponResult.OwnedDetail result) {
            return new OwnedCouponResponse(
                    result.id(), result.couponId(), result.couponName(),
                    result.discountType(), result.discountValue(),
                    result.minOrderAmount(), result.status(),
                    result.usedAt(), result.expiredAt(), result.issuedAt());
        }
    }

    public record OwnedCouponListResponse(
        List<OwnedCouponResponse> items
    ) {}
}
