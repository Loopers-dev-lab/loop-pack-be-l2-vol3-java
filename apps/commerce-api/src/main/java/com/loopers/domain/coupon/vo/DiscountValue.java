package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record DiscountValue(int value) {

    public DiscountValue {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
    }
}
