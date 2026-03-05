package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponDto {

    public record CreateRequest(
        @NotBlank String name,
        @NotNull DiscountType type,
        @Min(1) int value,
        @Min(0) int minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        @NotNull DiscountType type,
        @Min(1) int value,
        @Min(0) int minOrderAmount,
        @NotNull ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        String discountType,
        int discountValue,
        int minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(Coupon coupon) {
            return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt()
            );
        }
    }

    public record CouponIssueResponse(
        Long id,
        Long couponId,
        Long memberId,
        Long usedOrderId,
        String status,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponIssueResponse from(CouponIssue issue, ZonedDateTime now) {
            return new CouponIssueResponse(
                issue.getId(),
                issue.getCouponId(),
                issue.getMemberId(),
                issue.getUsedOrderId(),
                issue.getEffectiveStatus(now).name(),
                issue.getExpiredAt(),
                issue.getCreatedAt()
            );
        }
    }
}
