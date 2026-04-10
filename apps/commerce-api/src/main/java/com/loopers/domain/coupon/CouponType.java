package com.loopers.domain.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum CouponType {

    FIXED("정액 할인") {
        @Override
        public BigDecimal calculateDiscount(int value, BigDecimal totalAmount) {
            return totalAmount.min(BigDecimal.valueOf(value));
        }
    },
    RATE("정률 할인") {
        @Override
        public BigDecimal calculateDiscount(int value, BigDecimal totalAmount) {
            return totalAmount.multiply(BigDecimal.valueOf(value))
                    .divide(RATE_DENOMINATOR, 0, RoundingMode.DOWN);
        }
    };

    private static final BigDecimal RATE_DENOMINATOR = BigDecimal.valueOf(100);

    private final String description;

    CouponType(String description) {
        this.description = description;
    }

    public abstract BigDecimal calculateDiscount(int value, BigDecimal totalAmount);

    public String getDescription() {
        return description;
    }
}
