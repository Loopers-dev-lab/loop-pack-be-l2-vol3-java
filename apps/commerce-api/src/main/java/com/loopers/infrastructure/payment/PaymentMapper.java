package com.loopers.infrastructure.payment;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.payment.Payment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * PaymentMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class PaymentMapper {

    /**
     * Domain → JPA Entity
     */
    public PaymentEntity toEntity(Payment domain) {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(domain.getId());
        entity.setOrderId(domain.getOrderId());
        entity.setStatus(domain.getStatus());
        entity.setPaymentMethod(domain.getPaymentMethod());

        // Money VO → int 분해
        entity.setRequestedAmount(domain.getRequestedAmount());

        entity.setApprovedAmount(domain.getApprovedAmount());
        entity.setPgTxnId(domain.getPgTxnId());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setRequestedAt(domain.getRequestedAt());
        entity.setApprovedAt(domain.getApprovedAt());
        entity.setFailedAt(domain.getFailedAt());
        entity.setCanceledAt(domain.getCanceledAt());
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(domain.getDeletedAt());

        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public Payment toDomain(PaymentEntity entity) {
        // int → Money VO 재조합
        Money requestedAmount = new Money(entity.getRequestedAmount());

        return Payment.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                entity.getStatus(),
                entity.getPaymentMethod(),
                requestedAmount,
                entity.getApprovedAmount(),
                entity.getPgTxnId(),
                entity.getIdempotencyKey(),
                entity.getRequestedAt(),
                entity.getApprovedAt(),
                entity.getFailedAt(),
                entity.getCanceledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
