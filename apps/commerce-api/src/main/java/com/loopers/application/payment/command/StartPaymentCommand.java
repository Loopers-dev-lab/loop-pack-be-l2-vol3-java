package com.loopers.application.payment.command;

import com.loopers.domain.payment.CardType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

public record StartPaymentCommand(
        String memberId,
        UUID orderId,
        CardType cardType,
        String cardNo,
        int amount,
        String callbackUrl
) {
    public StartPaymentCommand {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "콜백 URL은 필수입니다.");
        }
    }
}
