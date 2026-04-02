package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.domain.coupon.DiscountType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;

public class AdminCouponV1Dto {

    public record CreateCouponTemplateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다")
        String name,

        @NotNull(message = "할인 타입은 필수입니다")
        DiscountType discountType,

        @NotNull(message = "할인 값은 필수입니다")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다")
        Integer discountValue,

        @NotNull(message = "최소 주문 금액은 필수입니다")
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다")
        Integer minOrderAmount,

        @NotNull(message = "만료일은 필수입니다")
        ZonedDateTime expiredAt,

        @Min(value = 1, message = "최대 발급 수량은 1 이상이어야 합니다")
        Integer maxIssuanceCount  // null이면 무제한
    ) {
    }

    public record UpdateCouponTemplateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다")
        String name,

        @NotNull(message = "할인 타입은 필수입니다")
        DiscountType discountType,

        @NotNull(message = "할인 값은 필수입니다")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다")
        Integer discountValue,

        @NotNull(message = "최소 주문 금액은 필수입니다")
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다")
        Integer minOrderAmount,

        @NotNull(message = "만료일은 필수입니다")
        ZonedDateTime expiredAt
    ) {
    }

    public record CouponTemplateResponse(
        Long id,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        Integer maxIssuanceCount,
        Integer issuedCount
    ) {
        public static CouponTemplateResponse from(CouponTemplateInfo info) {
            return new CouponTemplateResponse(
                info.id(),
                info.name(),
                info.discountType(),
                info.discountValue(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.maxIssuanceCount(),
                info.issuedCount()
            );
        }
    }

    public record CouponTemplateListResponse(
        List<CouponTemplateResponse> coupons
    ) {
        public static CouponTemplateListResponse from(List<CouponTemplateInfo> infos) {
            List<CouponTemplateResponse> responses = infos.stream()
                .map(CouponTemplateResponse::from)
                .toList();
            return new CouponTemplateListResponse(responses);
        }
    }
}
