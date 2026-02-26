package com.loopers.domain.model.order;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Money {

    private final int value;

    private Money(int value) {
        this.value = value;
    }

    public static Money of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
        return new Money(value);
    }

    public static Money zero() {
        return new Money(0);
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }

    public Money subtract(Money other) {
        int result = this.value - other.value;
        if (result < 0) {
            throw new IllegalStateException("차감 결과 금액이 음수가 될 수 없습니다.");
        }
        return new Money(result);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("곱할 수량은 0 이상이어야 합니다.");
        }
        return new Money(this.value * quantity);
    }
}
