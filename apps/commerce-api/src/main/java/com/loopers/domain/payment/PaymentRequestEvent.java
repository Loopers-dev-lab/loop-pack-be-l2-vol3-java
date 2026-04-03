package com.loopers.domain.payment;

/**
 * 결제 요청 이벤트.
 * PaymentFacade에서 Payment 저장 후 발행하며,
 * AFTER_COMMIT 시점에 PaymentEventListener가 수신하여 PG API를 호출한다.
 *
 * @param paymentId 생성된 결제 ID
 * @param orderId 주문 ID (재고 복구 시 OrderItem 조회용)
 * @param userId 결제 요청 사용자 ID (PG X-USER-ID 헤더에 전달)
 */
public record PaymentRequestEvent(
        Long paymentId,
        Long orderId,
        Long userId
) {}
