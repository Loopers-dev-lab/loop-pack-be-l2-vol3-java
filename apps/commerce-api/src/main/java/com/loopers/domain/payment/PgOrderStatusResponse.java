package com.loopers.domain.payment;

import java.util.List;

/**
 * PG 주문별 결제 상태 확인 응답 DTO (도메인 계층 표현).
 * transactionKey 미수신 시 orderId로 PG 측 결제 존재 여부를 확인한다.
 *
 * @param orderId 주문 ID
 * @param transactions 해당 주문의 결제 트랜잭션 목록 (PG에 접수된 건이 없으면 빈 리스트)
 */
public record PgOrderStatusResponse(
        String orderId,
        List<PgPaymentStatusResponse> transactions
) {

    // PG에 접수된 결제가 존재하는지 여부
    public boolean hasTransactions() {
        return transactions != null && !transactions.isEmpty();
    }

    // 최신 트랜잭션 (가장 마지막에 생성된 건)
    public PgPaymentStatusResponse latestTransaction() {
        if (!hasTransactions()) {
            return null;
        }
        return transactions.get(transactions.size() - 1);
    }
}
