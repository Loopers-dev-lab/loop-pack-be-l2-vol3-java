package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Money(int amount) {

    public Money {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
    }

    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(Money other) {
        if (this.amount < other.amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 음수가 될 수 없습니다.");
        }
        return new Money(this.amount - other.amount);
    }

    public Money multiply(int quantity) {
        return new Money(this.amount * quantity);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }
}
