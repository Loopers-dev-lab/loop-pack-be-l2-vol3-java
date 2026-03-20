package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponAdminV1Dto {

    public record CreateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다")
        CouponType type,

        @NotNull(message = "쿠폰 값은 필수입니다")
        @Positive(message = "쿠폰 값은 0보다 커야 합니다")
        Long value,

        Long minOrderAmount,

        @NotNull(message = "만료일은 필수입니다")
        ZonedDateTime expiredAt
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다")
        CouponType type,

        @NotNull(message = "쿠폰 값은 필수입니다")
        @Positive(message = "쿠폰 값은 0보다 커야 합니다")
        Long value,

        Long minOrderAmount,

        @NotNull(message = "만료일은 필수입니다")
        ZonedDateTime expiredAt
    ) {}

    public record CouponTemplateResponse(
        Long id,
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponTemplateResponse from(CouponInfo.CouponTemplateInfo info) {
            return new CouponTemplateResponse(
                info.id(), info.name(), info.type(), info.value(),
                info.minOrderAmount(), info.expiredAt(), info.createdAt()
            );
        }
    }

    public record CouponTemplateListResponse(
        List<CouponTemplateResponse> coupons,
        long totalCount,
        int page,
        int size
    ) {}

    public record IssuedCouponResponse(
        Long issuedCouponId,
        Long memberId,
        CouponStatus status,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static IssuedCouponResponse from(CouponInfo.IssuedCouponDetailInfo info) {
            return new IssuedCouponResponse(
                info.issuedCouponId(), info.memberId(), info.status(),
                info.usedAt(), info.createdAt()
            );
        }
    }

    public record IssuedCouponListResponse(
        List<IssuedCouponResponse> issues,
        long totalCount,
        int page,
        int size
    ) {}
}
