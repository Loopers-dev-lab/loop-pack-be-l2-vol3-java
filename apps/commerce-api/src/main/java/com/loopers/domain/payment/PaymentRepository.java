package com.loopers.domain.payment;

import java.util.Optional;

/**
 * 결제 영속성 인터페이스 (06 §10.1).
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findById(Long paymentId);

    /**
     * 해당 주문에 PENDING 상태 결제가 있는지 조회. 멱등성·중복 요청 차단용 (Phase 7).
     */
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /**
     * 주문 ID로 결제 조회 (폴링/복구 시 사용). 최신 1건 등 정책에 따라 확장 가능.
     */
    Optional<PaymentModel> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
