package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Stock(int value) {

    public Stock {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }

    public Stock decrease(int quantity) {
        if (this.value < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new Stock(this.value - quantity);
    }

    public Stock increase(int quantity) {
        return new Stock(this.value + quantity);
    }
}
