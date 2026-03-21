package com.loopers.interfaces.api.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class PaymentCallbackDto {

    public record CallbackRequest(
            String memberId,
            String transactionKey
    ) {
        public CallbackRequest {
            if (transactionKey == null || transactionKey.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "콜백 거래 키는 필수입니다.");
            }
        }
    }
}
