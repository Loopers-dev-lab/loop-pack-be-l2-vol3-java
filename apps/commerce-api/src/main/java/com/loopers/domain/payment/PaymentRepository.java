package com.loopers.domain.payment;

import com.loopers.support.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 리포지토리 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에 정의되며,
 * infrastructure 계층의 {@code PaymentRepositoryImpl}이 구현한다.
 * </p>
 */
public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findById(Long paymentId);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    List<PaymentModel> findAllByOrderId(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    int casUpdateStatus(Long paymentId, PaymentStatus from, PaymentStatus to, String failureReason);

    List<PaymentModel> findAllRequestedBefore(LocalDateTime before);
}
