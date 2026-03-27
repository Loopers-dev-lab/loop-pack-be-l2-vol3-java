package com.loopers.infrastructure.persistence.jpa.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.Payment;

/**
 * Payment 도메인 <-> JPA 엔티티 매퍼.
 */
public class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentJpaEntity toJpaEntity(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity(
                payment.getOrderId(),
                payment.getTransactionId(),
                payment.getAmount().amount(),
                payment.getStatus()
        );
        entity.setFailReason(payment.getFailReason());
        return entity;
    }

    public static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                entity.getTransactionId(),
                new Money(entity.getAmount()),
                entity.getStatus(),
                entity.getFailReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
