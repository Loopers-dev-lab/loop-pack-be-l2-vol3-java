package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;

/**
 * PG 시뮬레이터 결제 응답 DTO.
 * <p>
 * PG API가 반환하는 형식 그대로를 표현하며, domain 레이어에서는 사용하지 않는다.
 * {@link #toGatewayResult()}로 도메인 타입으로 변환한다.
 * </p>
 */
public record PgPaymentResponse(
        String transactionKey,
        boolean success,
        String status,
        String reason
) {

    public GatewayPaymentResult toGatewayResult() {
        return new GatewayPaymentResult(transactionKey, success, status, reason);
    }
}
