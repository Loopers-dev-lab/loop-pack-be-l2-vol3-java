package com.loopers.infrastructure.payment;

import com.loopers.support.enums.CardType;

import java.math.BigDecimal;

/**
 * PG 시뮬레이터 결제 요청 DTO.
 * <p>
 * PG API 스펙에 맞춘 요청 형식이며, domain 레이어에서는 사용하지 않는다.
 * domain 타입에서 PG 스펙 타입으로의 변환은 infrastructure 책임이다.
 * </p>
 */
public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        BigDecimal amount,
        String callbackUrl
) {

    public static PgPaymentRequest from(Long orderId, CardType cardType, String cardNo,
                                         BigDecimal amount, String callbackUrl) {
        String orderIdStr = String.format("%06d", orderId);
        return new PgPaymentRequest(orderIdStr, cardType.name(), cardNo, amount, callbackUrl);
    }
}
