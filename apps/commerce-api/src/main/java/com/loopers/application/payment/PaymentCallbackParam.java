package com.loopers.application.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * PG 콜백 요청 파라미터 (06 §3, §10.4).
 * amount: PG 측 결제 금액. 있으면 주문 금액과 대조 (06-payment-change-issues §4.2).
 */
public record PaymentCallbackParam(
        Long orderId,
        boolean success,
        String pgTransactionId,
        String failureReason,
        Long amount
) {
    public PaymentCallbackParam {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "orderId는 필수입니다.");
        }
        if (amount != null && amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "amount는 0 이상이어야 합니다.");
        }
        if (success && (pgTransactionId == null || pgTransactionId.isBlank())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "pgTransactionId는 필수입니다.");
        }
    }
}
