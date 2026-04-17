package com.loopers.infrastructure.pg;

/**
 * PG 추상화 인터페이스 (Strategy Pattern).
 * Simulator, Toss Sandbox 등 PG별 구현체가 이 인터페이스를 구현한다.
 */
public interface PgClient {

    /**
     * 결제 요청. PG 시뮬레이터는 PENDING을, Toss Sandbox는 즉시 결과를 반환한다.
     */
    PgPaymentResponse requestPayment(PgPaymentRequest request);

    /**
     * transactionKey 기반 결제 상태 확인.
     */
    PgPaymentStatusResponse getPaymentStatus(String transactionKey);

    /**
     * orderId 기반 결제 상태 확인.
     */
    PgPaymentStatusResponse getPaymentByOrderId(String orderId);

    /**
     * PG 제공사 이름 (SIMULATOR, TOSS 등).
     */
    String getProviderName();
}
