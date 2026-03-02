package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Money(int value) {

    public Money {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
        }
    }

    public Money multiply(int quantity) {
        return new Money(this.value * quantity);
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }
}
