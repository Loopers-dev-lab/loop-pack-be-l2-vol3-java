package com.loopers.domain.model.product;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Stock {

    private final int value;

    private Stock(int value) {
        this.value = value;
    }

    public static Stock of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
        return new Stock(value);
    }

    public Stock decrease(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다.");
        }
        if (!hasEnough(quantity)) {
            throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + this.value + ", 요청 수량: " + quantity);
        }
        return new Stock(this.value - quantity);
    }

    public Stock increase(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("증가 수량은 1 이상이어야 합니다.");
        }
        return new Stock(this.value + quantity);
    }

    public boolean hasEnough(int quantity) {
        return this.value >= quantity;
    }
}
