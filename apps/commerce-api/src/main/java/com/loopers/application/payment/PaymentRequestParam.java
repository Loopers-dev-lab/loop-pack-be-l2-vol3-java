package com.loopers.application.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PG 호출 직전에 필요한 파라미터 (트랜잭션 밖에서 사용).
 * PENDING 저장 TX에서 조회한 주문 금액·callbackUrl 등.
 * 금액: 최소 통화 단위(원) 정수만 사용. 소수 이하는 반올림 (06-payment-change-issues §1.2).
 */
public record PaymentRequestParam(
        Long orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {
    public static PaymentRequestParam of(Long orderId, String cardType, String cardNo,
                                         BigDecimal finalAmount, String callbackUrl) {
        if (finalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액이 유효하지 않습니다.");
        }
        final long amountInWon;
        try {
            amountInWon = finalAmount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액이 유효하지 않습니다.", e);
        }
        return new PaymentRequestParam(
                orderId,
                cardType,
                cardNo,
                amountInWon,
                callbackUrl
        );
    }

    /**
     * 민감정보(cardNo)는 로그/에러 메시지에서 평문 노출되지 않도록 toString에 포함하지 않는다.
     */
    @Override
    public String toString() {
        return "PaymentRequestParam[orderId=%s, cardType=%s, amount=%d, callbackUrl=%s]"
                .formatted(orderId, cardType, amount, callbackUrl);
    }
}
