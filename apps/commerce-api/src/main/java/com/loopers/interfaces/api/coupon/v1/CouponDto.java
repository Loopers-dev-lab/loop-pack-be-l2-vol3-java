package com.loopers.interfaces.api.coupon.v1;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.coupon.CouponCommand.CreateCouponCommand;
import com.loopers.application.coupon.CouponCommand.UpdateCouponCommand;
import com.loopers.application.coupon.CouponResult;
import com.loopers.domain.coupon.CouponType;

public class CouponDto {

    public record CreateCouponRequest(
            @NotBlank(message = "쿠폰명은 필수입니다.") String name,
            @NotNull(message = "쿠폰 유형은 필수입니다.") CouponType type,
            @NotNull(message = "할인값은 필수입니다.") Long discountValue,
            Long maxDiscountPrice,
            @NotNull(message = "최소 주문 금액은 필수입니다.") Long minOrderPrice,
            @NotNull(message = "만료일은 필수입니다.") ZonedDateTime expiredAt
    ) {

        public CreateCouponCommand toCreateCouponCommand() {
            return new CreateCouponCommand(name, type, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        }
    }

    public record CreateCouponResponse(Long couponId) {

        public static CreateCouponResponse from(CouponResult result) {
            return new CreateCouponResponse(result.id());
        }
    }

    public record UpdateCouponRequest(
            @NotBlank(message = "쿠폰명은 필수입니다.") String name,
            @NotNull(message = "할인값은 필수입니다.") Long discountValue,
            Long maxDiscountPrice,
            @NotNull(message = "최소 주문 금액은 필수입니다.") Long minOrderPrice,
            @NotNull(message = "만료일은 필수입니다.") ZonedDateTime expiredAt
    ) {

        public UpdateCouponCommand toUpdateCouponCommand(Long couponId) {
            return new UpdateCouponCommand(couponId, name, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        }
    }

    public record CouponResponse(
            Long id,
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt,
            ZonedDateTime createdAt,
            ZonedDateTime deletedAt
    ) {

        public static CouponResponse from(CouponResult result) {
            return new CouponResponse(
                    result.id(),
                    result.name(),
                    result.type(),
                    result.discountValue(),
                    result.maxDiscountPrice(),
                    result.minOrderPrice(),
                    result.expiredAt(),
                    result.createdAt(),
                    result.deletedAt()
            );
        }

        public static List<CouponResponse> from(List<CouponResult> results) {
            return results.stream()
                    .map(CouponResponse::from)
                    .toList();
        }
    }
}
