package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @NotNull(message = "할인 값은 필수입니다.")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
        Long value,

        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        Long minOrderAmount,

        @NotNull(message = "만료 시각은 필수입니다.")
        @Future(message = "만료 시각은 미래여야 합니다.")
        ZonedDateTime expiredAt
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        String name,

        @NotNull(message = "쿠폰 타입은 필수입니다.")
        CouponType type,

        @NotNull(message = "할인 값은 필수입니다.")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
        Long value,

        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        Long minOrderAmount,

        @NotNull(message = "만료 시각은 필수입니다.")
        @Future(message = "만료 시각은 미래여야 합니다.")
        ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record CouponIssueResponse(
        Long id,
        Long userId,
        UserCouponStatus status,
        Long orderId,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt
    ) {
        public static CouponIssueResponse from(UserCouponInfo info) {
            return new CouponIssueResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.orderId(),
                info.issuedAt(),
                info.usedAt()
            );
        }
    }
}
