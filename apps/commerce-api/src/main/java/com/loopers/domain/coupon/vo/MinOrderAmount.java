package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record MinOrderAmount(int value) {

    public MinOrderAmount {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
    }
}
