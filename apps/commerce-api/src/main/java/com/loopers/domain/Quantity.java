package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }

    public Quantity add(int amount) {
        return new Quantity(this.value + amount);
    }
}
