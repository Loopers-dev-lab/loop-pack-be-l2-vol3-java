package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return paymentJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public Optional<PaymentModel> findByPgTransactionId(String pgTransactionId) {
        return paymentJpaRepository.findByPgTransactionIdAndDeletedAtIsNull(pgTransactionId);
    }
}
