package com.loopers.domain.product;

import java.math.BigDecimal;

/**
 * 가격 값 객체.
 * 0 이상만 허용한다.
 */
public record Money(BigDecimal value) {

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("가격은 null일 수 없습니다.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }
}
