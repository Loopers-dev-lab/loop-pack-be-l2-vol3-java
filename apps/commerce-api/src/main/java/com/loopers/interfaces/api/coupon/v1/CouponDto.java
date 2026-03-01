package com.loopers.interfaces.api.coupon.v1;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.coupon.CouponCommand.CreateCouponCommand;
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
}
