package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 * 금액 Value Object
 */
public record Money(Integer value) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (value == null || value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Money.AMOUNT_INVALID);
        }
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }

    public Money multiply(int multiplier) {
        return new Money(this.value * multiplier);
    }
}
