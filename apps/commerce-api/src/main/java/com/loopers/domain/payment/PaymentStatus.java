package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED;

    public static PaymentStatus from(String status) {
        try {
            return PaymentStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 결제 상태값입니다: " + status);
        }
    }
}
