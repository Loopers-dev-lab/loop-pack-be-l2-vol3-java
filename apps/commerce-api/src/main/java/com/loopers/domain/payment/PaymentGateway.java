package com.loopers.domain.payment;

import java.util.List;

/**
 * 외부 결제 시스템(PG)과의 연동을 추상화하는 인터페이스.
 * Domain 계층에 위치하며, Infrastructure 계층에서 구현한다.
 */
public interface PaymentGateway {

    /**
     * PG에 결제를 요청한다.
     * @return PG에서 발급한 트랜잭션 키 (PENDING 상태)
     * @throws PaymentGatewayException PG 요청 실패 시
     */
    String requestPayment(Long userId, Long orderId, CardType cardType, String cardNo, int amount);

    /**
     * PG에서 트랜잭션 상태를 조회한다.
     * @return 트랜잭션 상태 정보
     */
    TransactionResult getTransactionStatus(Long userId, String transactionKey);

    /**
     * PG에서 orderId로 결제 목록을 조회한다.
     * PENDING 상태의 Payment(transactionKey 미보유) 복구 시 사용.
     */
    List<TransactionResult> getTransactionsByOrderId(Long userId, Long orderId);

    record TransactionResult(
        String transactionKey,
        String status,
        String reason
    ) {
        public boolean isSuccess() {
            return "SUCCESS".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }
}
