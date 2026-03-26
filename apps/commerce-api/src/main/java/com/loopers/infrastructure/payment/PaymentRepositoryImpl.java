package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component
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
    public Optional<PaymentModel> findByPgPaymentKey(String pgPaymentKey) {
        return paymentJpaRepository.findByPgPaymentKeyAndDeletedAtIsNull(pgPaymentKey);
    }

    @Override
    public List<PaymentModel> findRecoverTargets(ZonedDateTime threshold, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        return paymentJpaRepository.findRecoverTargets(
            List.of(PaymentStatus.REQUESTED, PaymentStatus.PENDING),
            threshold,
            PageRequest.of(0, safeSize)
        );
    }
}
