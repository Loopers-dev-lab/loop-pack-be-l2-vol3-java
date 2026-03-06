package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueRequest(
        @NotNull Long couponTemplateId
    ) {}

    public record CreateTemplateRequest(
        @NotBlank String name,
        @NotNull String discountType,
        @NotNull Long discountValue,
        @NotNull Long minOrderAmount,
        Long maxDiscountAmount,
        @NotNull Integer validDays
    ) {}

    public record CouponResponse(
        Long id,
        Long userId,
        Long couponTemplateId,
        CouponStatus status,
        ZonedDateTime issuedAt,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.userId(),
                info.couponTemplateId(),
                info.status(),
                info.issuedAt(),
                info.expiredAt(),
                info.usedAt()
            );
        }
    }
}
