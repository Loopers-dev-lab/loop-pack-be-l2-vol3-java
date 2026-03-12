package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 수량을 나타내는 Value Object.
 * 수량은 항상 1 이상이어야 한다.
 */
public record Quantity(int value) {

    public Quantity {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}