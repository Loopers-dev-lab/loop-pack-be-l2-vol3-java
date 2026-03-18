package com.loopers.domain.payment.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record RefOrderId(Long value) {

    public RefOrderId {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refOrderId가 비어 있습니다");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "refOrderId는 양수여야 합니다: " + value);
        }
    }
}
