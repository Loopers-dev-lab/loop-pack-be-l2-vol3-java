package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;

public record PaymentStatusResult(
        String paymentStatus,
        String failureCode,
        String failureMessage) {

    public static PaymentStatusResult from(PaymentModel payment) {
        return new PaymentStatusResult(
                payment.getStatus().name(),
                payment.getFailureCode(),
                payment.isFailed() ? toCustomerMessage(payment.getFailureCode()) : null);
    }

    private static String toCustomerMessage(String code) {
        if (code == null) {
            return "결제 처리 중 오류가 발생했습니다. 다시 시도해주세요.";
        }
        return switch (code) {
            case "LIMIT_EXCEEDED" -> "카드 한도가 초과되었습니다. 다른 카드로 결제해주세요.";
            case "INVALID_CARD" -> "카드 정보가 올바르지 않습니다. 카드 번호를 확인해주세요.";
            case "PG_REQUEST_FAILED" -> "결제 요청에 실패했습니다. 다시 시도해주세요.";
            default -> "결제 처리 중 오류가 발생했습니다. 다시 시도해주세요.";
        };
    }
}
