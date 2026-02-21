package com.loopers.domain.common.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class Money {

    @Column
    private int value;

    protected Money() {}

    public Money(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다: " + value);
        }
        this.value = value;
    }

    public static Money zero() {
        return new Money(0);
    }

    public int toInt() {
        return this.value;
    }

    public Money plus(Money other) {
        return new Money(this.value + other.value);
    }

    public Money minus(Money other) {
        return new Money(this.value - other.value);
    }

    public Money multiply(int multiplier) {
        return new Money(this.value * multiplier);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.value >= other.value;
    }

    public boolean isZero() {
        return this.value == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return value == money.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
