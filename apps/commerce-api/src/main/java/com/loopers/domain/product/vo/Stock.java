package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Column(name = "stock_quantity", nullable = false)
    private int quantity;

    public Stock(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    public boolean hasEnough(int amount) {
        return this.quantity >= amount;
    }

    public Stock decrease(int amount) {
        if (!hasEnough(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new Stock(this.quantity - amount);
    }

    public Stock increase(int amount) {
        return new Stock(this.quantity + amount);
    }
}
