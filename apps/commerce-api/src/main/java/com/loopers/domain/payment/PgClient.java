package com.loopers.domain.payment;

/**
 * PG(Payment Gateway) 외부 시스템과의 통신 인터페이스 (Port).
 * 인프라 레이어에서 RestClient + Resilience4j로 구현한다.
 */
public interface PgClient {

    /**
     * PG에 결제를 요청한다.
     * PG는 즉시 "접수됨"을 반환하고, 1~5초 후 콜백으로 결과를 전달한다.
     */
    PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request);

    /**
     * PG에 결제 상태를 확인한다 (transactionKey 기준).
     * 콜백 미수신 시 폴링 스케줄러에서 사용한다.
     */
    PgPaymentStatusResponse getPaymentStatus(Long userId, String transactionKey);

    /**
     * PG에 결제 상태를 확인한다 (orderId 기준).
     * transactionKey가 없는 경우 (응답 미수신) orderId로 PG 측 결제 존재 여부를 확인한다.
     */
    PgOrderStatusResponse getPaymentStatusByOrderId(Long userId, String orderId);
}
