package com.loopers.domain.common.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class Quantity {

    @Column
    private int value;

    protected Quantity() {}

    public Quantity(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다: " + value);
        }
        this.value = value;
    }

    public static Quantity zero() {
        return new Quantity(0);
    }

    public int toInt() {
        return this.value;
    }

    public Quantity plus(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Quantity minus(Quantity other) {
        return new Quantity(this.value - other.value);
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        return this.value >= other.value;
    }

    public boolean isPositive() {
        return this.value > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quantity quantity = (Quantity) o;
        return this.value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Quantity{value=" + value + "}";
    }
}
