package com.loopers.interfaces.api.coupon;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminCouponTemplateResponse {

    public record TemplateDetail(
            Long id,
            String name,
            String description,
            String discountType,
            int discountValue,
            Integer maxDiscountAmount,
            int minOrderAmount,
            int maxIssueCount,
            int maxIssueCountPerUser,
            ZonedDateTime validFrom,
            ZonedDateTime validTo,
            String status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {}

    public record TemplateListResponse(
            List<TemplateDetail> templates,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record IssuedCouponSummary(
            Long issuedCouponId, Long userId, String status,
            Long orderId, ZonedDateTime usedAt, ZonedDateTime createdAt) {}

    public record IssuedCouponListResponse(
            List<IssuedCouponSummary> issuedCoupons,
            int page, int size, long totalElements, int totalPages) {}
}
