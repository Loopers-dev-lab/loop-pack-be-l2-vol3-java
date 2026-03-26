package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;

import java.util.List;

/**
 * PG 시뮬레이터 주문별 결제 목록 조회 응답 DTO.
 * <p>
 * {@code GET /api/v1/payments?orderId={orderId}} 응답 형식.
 * </p>
 */
public record PgOrderPaymentsResponse(
        String orderId,
        List<PgTransactionSummary> transactions
) {

    public record PgTransactionSummary(
            String transactionKey,
            String status,
            String reason
    ) {

        public GatewayPaymentResult toGatewayResult() {
            boolean success = "SUCCESS".equals(status);
            return new GatewayPaymentResult(transactionKey, success, status, reason);
        }
    }

    public List<GatewayPaymentResult> toGatewayResults() {
        if (transactions == null) return List.of();
        return transactions.stream()
                .map(PgTransactionSummary::toGatewayResult)
                .toList();
    }
}
