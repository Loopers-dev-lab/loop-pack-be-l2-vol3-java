package com.loopers.domain.payment;

import java.util.List;

/**
 * 테스트용 PaymentGateway 구현체.
 * 성공/실패/타임아웃 시나리오를 제어할 수 있다.
 */
public class FakePaymentGateway implements PaymentGateway {

    private boolean shouldFail = false;
    private boolean shouldTimeout = false;
    private String nextTransactionKey = "20250316:TR:fake01";
    private TransactionResult nextTransactionResult;
    private List<TransactionResult> nextOrderResults;

    public void willSucceed(String transactionKey) {
        this.shouldFail = false;
        this.shouldTimeout = false;
        this.nextTransactionKey = transactionKey;
    }

    public void willFail() {
        this.shouldFail = true;
        this.shouldTimeout = false;
    }

    public void willTimeout() {
        this.shouldTimeout = true;
        this.shouldFail = false;
    }

    public void setNextTransactionResult(TransactionResult result) {
        this.nextTransactionResult = result;
    }

    public void setNextOrderResults(List<TransactionResult> results) {
        this.nextOrderResults = results;
    }

    @Override
    public String requestPayment(Long userId, Long orderId, CardType cardType, String cardNo, int amount) {
        if (shouldTimeout) {
            throw new PaymentGatewayException("PG 요청 타임아웃");
        }
        if (shouldFail) {
            throw new PaymentGatewayException("PG 요청 실패: 현재 서버가 불안정합니다.");
        }
        return nextTransactionKey;
    }

    @Override
    public TransactionResult getTransactionStatus(Long userId, String transactionKey) {
        if (shouldTimeout) {
            throw new PaymentGatewayException("PG 조회 타임아웃");
        }
        if (nextTransactionResult != null) {
            return nextTransactionResult;
        }
        return new TransactionResult(transactionKey, "PENDING", null);
    }

    @Override
    public List<TransactionResult> getTransactionsByOrderId(Long userId, Long orderId) {
        if (shouldTimeout) {
            throw new PaymentGatewayException("PG orderId 조회 타임아웃");
        }
        if (nextOrderResults != null) {
            return nextOrderResults;
        }
        return List.of();
    }
}
