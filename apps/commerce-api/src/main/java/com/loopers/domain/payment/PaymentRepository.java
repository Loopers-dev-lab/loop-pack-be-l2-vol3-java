package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByOrderId(Long orderId);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    List<PaymentModel> findAllByStatus(PaymentStatus status);

    /**
     * 조건부 UPDATE — 현재 상태가 허용된 상태 중 하나일 때만 상태를 변경한다.
     * Callback/Batch 동시 실행 방지용.
     *
     * @return 업데이트된 행 수 (0이면 이미 처리된 건)
     */
    int updateStatusConditionally(Long paymentId, PaymentStatus newStatus,
                                   List<PaymentStatus> allowedCurrentStatuses);
}
