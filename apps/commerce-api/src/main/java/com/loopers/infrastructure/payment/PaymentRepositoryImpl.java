package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey);
    }

    @Override
    public List<PaymentModel> findAllByStatus(PaymentStatus status) {
        return paymentJpaRepository.findAllByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public int updateStatusConditionally(Long paymentId, PaymentStatus newStatus,
                                          List<PaymentStatus> allowedCurrentStatuses) {
        return paymentJpaRepository.updateStatusConditionally(paymentId, newStatus, allowedCurrentStatuses);
    }
}
