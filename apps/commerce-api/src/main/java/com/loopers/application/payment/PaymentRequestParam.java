package com.loopers.application.payment;

import java.math.BigDecimal;

/**
 * PG 호출 직전에 필요한 파라미터 (트랜잭션 밖에서 사용).
 * PENDING 저장 TX에서 조회한 주문 금액·callbackUrl 등.
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
            throw new IllegalArgumentException("주문 금액은 null일 수 없습니다.");
        }
        return new PaymentRequestParam(
                orderId,
                cardType,
                cardNo,
                finalAmount.longValue(),
                callbackUrl
        );
    }
}
