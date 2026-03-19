package com.loopers.domain.payment;

/**
 * PG사 통신 포트 (Domain Layer)
 *
 * Infrastructure에서 HTTP 구현체(Adapter)가 이 인터페이스를 구현한다.
 * Domain은 HTTP, RestTemplate 등 기술에 의존하지 않는다.
 */
public interface PaymentClient {

    /** PG 결제 승인 요청 */
    PgApproveResult approve(PgApproveRequest request);

    /** PG 결제 상태 조회 (타임아웃/불확실 상태 확인용) */
    PgQueryResult query(String transactionKey, Long userId);

    /** PG 결제 취소 요청 (승인 후 주문 취소 시) */
    PgCancelResult cancel(String transactionKey, Long userId);
}
