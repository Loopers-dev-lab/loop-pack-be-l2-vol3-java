package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        if (payment.id() != null) {
            Optional<PaymentEntity> existing = paymentJpaRepository.findById(payment.id())
                    .filter(entity -> entity.getDeletedAt() == null);
            if (existing.isPresent()) {
                PaymentEntity entity = existing.get();
                entity.updateFrom(payment);
                return paymentJpaRepository.saveAndFlush(entity).toDomain();
            }
        }

        return paymentJpaRepository.saveAndFlush(PaymentEntity.from(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return paymentJpaRepository.findById(id)
                .filter(entity -> entity.getDeletedAt() == null)
                .map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByMemberIdAndOrderId(String memberId, UUID orderId) {
        return paymentJpaRepository.findByMemberIdAndOrderIdAndDeletedAtIsNull(memberId, orderId)
                .map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByMemberIdAndOrderIdForUpdate(String memberId, UUID orderId) {
        return paymentJpaRepository.findByMemberIdAndOrderIdForUpdate(memberId, orderId)
                .map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByPgTransactionKey(String memberId, String pgTransactionKey) {
        return paymentJpaRepository.findByMemberIdAndPgTransactionKeyAndDeletedAtIsNull(memberId, pgTransactionKey)
                .map(PaymentEntity::toDomain);
    }

    @Override
    public List<Payment> findAllByOrderId(UUID orderId) {
        return paymentJpaRepository.findAllByOrderIdAndDeletedAtIsNull(orderId).stream()
                .map(PaymentEntity::toDomain)
                .toList();
    }

    @Override
    public List<Payment> findAllByStatusIn(List<PaymentStatus> statuses) {
        return paymentJpaRepository.findAllByStatusInAndDeletedAtIsNull(statuses).stream()
                .map(PaymentEntity::toDomain)
                .toList();
    }
}
