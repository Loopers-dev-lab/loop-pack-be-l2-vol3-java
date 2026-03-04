package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest() {

    // Command

    public record Register(
            @NotBlank(message = "쿠폰명은 필수입니다")
            @Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다")
            String name,

            @NotNull(message = "쿠폰 유형은 필수입니다")
            CouponType type,

            @NotNull(message = "할인값은 필수입니다")
            @Positive(message = "할인값은 1 이상이어야 합니다")
            Integer value,

            @PositiveOrZero(message = "최소 주문 금액은 0 이상이어야 합니다")
            BigDecimal minOrderAmount,

            @NotNull(message = "최대 발급 수량은 필수입니다")
            @Positive(message = "최대 발급 수량은 1 이상이어야 합니다")
            Integer maxIssueCount,

            @NotNull(message = "만료일은 필수입니다")
            @Future(message = "만료일은 현재 이후여야 합니다")
            LocalDateTime expiredAt
    ) {
        public CouponCommand.Register toCommand() {
            return CouponCommand.Register.of(name, type, value, minOrderAmount, maxIssueCount, expiredAt);
        }
    }
}
