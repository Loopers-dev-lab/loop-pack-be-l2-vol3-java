package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Stock(int quantity) {

    public Stock {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }

    public Stock decrease(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. (현재: %d, 요청: %d)", this.quantity, amount));
        }
        return new Stock(this.quantity - amount);
    }

    public Stock increase(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        return new Stock(this.quantity + amount);
    }
}
