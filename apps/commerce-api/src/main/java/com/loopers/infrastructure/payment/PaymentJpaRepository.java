package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<PaymentModel> findByOrderIdAndDeletedAtIsNull(Long orderId);

    Optional<PaymentModel> findByPgTransactionIdAndDeletedAtIsNull(String pgTransactionId);
}
