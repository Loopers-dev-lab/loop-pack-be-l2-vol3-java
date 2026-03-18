package com.loopers.domain.payment;

public enum PaymentStatus {
    /**
     * PG 요청 전 또는 PG 요청 자체가 실패(타임아웃, 서킷 오픈 등)한 상태.
     * 재시도/복구 대상.
     */
    PENDING,

    /**
     * PG에 요청이 접수되어 transactionKey를 수신한 상태.
     * 콜백 대기 중.
     */
    IN_PROGRESS,

    /**
     * 콜백/조회를 통해 결제 성공이 확인된 상태.
     */
    PAID,

    /**
     * 콜백/조회를 통해 결제 실패가 확인된 상태.
     * (한도 초과, 잘못된 카드 등)
     */
    FAILED
}
