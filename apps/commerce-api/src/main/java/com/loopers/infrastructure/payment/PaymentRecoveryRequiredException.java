package com.loopers.infrastructure.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class PaymentRecoveryRequiredException extends CoreException {
    public PaymentRecoveryRequiredException(String message) {
        super(ErrorType.INTERNAL_ERROR, message);
    }
}
