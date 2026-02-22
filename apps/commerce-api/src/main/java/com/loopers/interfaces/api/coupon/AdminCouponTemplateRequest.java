package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;

public class AdminCouponTemplateRequest {

    public record CreateTemplateRequest(
            String name,
            String description,
            String discountType,
            int discountValue,
            Integer maxDiscountAmount,
            int minOrderAmount,
            int maxIssueCount,
            int maxIssueCountPerUser,
            ZonedDateTime validFrom,
            ZonedDateTime validTo
    ) {}

    public record UpdateTemplateRequest(
            String name,
            String description,
            String discountType,
            int discountValue,
            Integer maxDiscountAmount,
            int minOrderAmount
    ) {}
}
