package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            PaymentEntity entity = PaymentEntity.create(payment);
            return paymentJpaRepository.save(entity).toDomain();
        }

        PaymentEntity entity = paymentJpaRepository.findById(payment.getId())
            .orElseThrow();
        entity.update(payment);
        return entity.toDomain();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id)
            .map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByRefOrderId(orderId)
            .map(PaymentEntity::toDomain);
    }

    @Override
    public List<Payment> findPendingBefore(ZonedDateTime threshold) {
        return paymentJpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold)
            .stream()
            .map(PaymentEntity::toDomain)
            .toList();
    }
}
