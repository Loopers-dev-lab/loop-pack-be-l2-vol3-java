package com.loopers.application.payment.command;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CompletePaymentCommand(
        String memberId,
        String transactionKey
) {
    public CompletePaymentCommand {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다.");
        }
    }
}
