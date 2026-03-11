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

    public Money subtract(Money other) {
        int result = this.value - other.value;
        return new Money(Math.max(result, 0));
    }

    public Money multiply(int multiplier) {
        return new Money(this.value * multiplier);
    }

    public Money divide(int divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return new Money(this.value / divisor);
    }

    public boolean isLessThan(Money other) {
        return this.value < other.value;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.value >= other.value;
    }
}
