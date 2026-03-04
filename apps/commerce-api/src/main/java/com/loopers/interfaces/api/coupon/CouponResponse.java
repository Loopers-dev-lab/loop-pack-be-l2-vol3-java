package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponResponse {

    public record IssuedCouponDetail(
            Long issuedCouponId,
            Long couponTemplateId,
            String couponName,
            String discountType,
            int discountValue,
            Integer maxDiscountAmount,
            String status,
            ZonedDateTime usedAt,
            ZonedDateTime createdAt
    ) {}

    public record CouponListResponse(List<IssuedCouponDetail> coupons) {}

    public record IssueCouponResponse(Long issuedCouponId, String status) {}

    public record AvailableCouponDetail(
            Long couponTemplateId, String name, String description,
            String discountType, int discountValue,
            Integer maxDiscountAmount, int minOrderAmount,
            ZonedDateTime validFrom, ZonedDateTime validTo
    ) {}

    public record AvailableCouponListResponse(List<AvailableCouponDetail> coupons) {}
}
