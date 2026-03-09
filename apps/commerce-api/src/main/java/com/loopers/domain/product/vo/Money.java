package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Money(int value) {

    public Money {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    public Money multiply(int quantity) {
        return new Money(this.value * quantity);
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }

    public Money subtract(Money other) {
        return new Money(Math.max(this.value - other.value, 0));
    }
}
