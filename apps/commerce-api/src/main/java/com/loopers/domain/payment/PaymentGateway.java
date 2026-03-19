package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;

public interface PaymentGateway {
    PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request);

    PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request);

    PaymentGatewayTransaction getPayment(String memberId, String transactionKey);

    List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference);

    record PaymentGatewayRequest(
            String memberId,
            String orderReference,
            CardType cardType,
            String cardNo,
            int amount,
            String callbackUrl
    ) {
        private static final String CARD_NO_PATTERN = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$";

        public PaymentGatewayRequest {
            if (memberId == null || memberId.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
            }
            if (orderReference == null || orderReference.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 참조값은 필수입니다.");
            }
            if (cardType == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
            }
            if (cardNo == null || !cardNo.matches(CARD_NO_PATTERN)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다.");
            }
            if (amount <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
            }
            if (callbackUrl == null || callbackUrl.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "콜백 URL은 필수입니다.");
            }
        }
    }

    record PaymentGatewayCancelRequest(
            String memberId,
            String transactionKey
    ) {
        public PaymentGatewayCancelRequest {
            if (memberId == null || memberId.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
            }
            if (transactionKey == null || transactionKey.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다.");
            }
        }
    }

    record PaymentGatewayTransaction(
            String transactionKey,
            String orderReference,
            PaymentStatus status,
            String reason
    ) {
        public PaymentGatewayTransaction {
            if (transactionKey == null || transactionKey.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다.");
            }
            if (orderReference == null || orderReference.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 참조값은 필수입니다.");
            }
            if (status == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "결제 상태는 필수입니다.");
            }
            if ((status == PaymentStatus.FAILED || status == PaymentStatus.CANCEL_FAILED)
                    && (reason == null || reason.isBlank())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "실패 상태에는 사유가 필수입니다.");
            }
        }
    }
}
