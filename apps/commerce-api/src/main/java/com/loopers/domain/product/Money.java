package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    public static final Money ZERO = new Money(0);

    @Column(name = "price", nullable = false)
    private int value;

    public Money(int value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        this.value = value;
    }

    public int value() {
        return value;
    }

    public Money multiply(int quantity) {
        return new Money(this.value * quantity);
    }

    public Money add(Money other) {
        return new Money(this.value + other.value);
    }

    public Money divide(int divisor) {
        if (divisor == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "0으로 나눌 수 없습니다.");
        }
        return new Money(this.value / divisor);
    }

    public Money subtract(Money other) {
        return new Money(Math.max(this.value - other.value, 0));
    }

    public Money min(Money other) {
        return this.value <= other.value ? this : other;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return value == money.value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
