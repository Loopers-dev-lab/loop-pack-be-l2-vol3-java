package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CompensationDlq;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class CompensationDlqMapper {

    public CompensationDlqEntity toEntity(CompensationDlq domain) {
        CompensationDlqEntity entity = new CompensationDlqEntity();
        entity.setId(domain.getId());
        entity.setOrderId(domain.getOrderId());
        entity.setPaymentId(domain.getPaymentId());
        entity.setFailureReason(domain.getFailureReason());
        entity.setRetryCount(domain.getRetryCount());
        entity.setMaxRetries(domain.getMaxRetries());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : ZonedDateTime.now());
        entity.setLastAttemptedAt(domain.getLastAttemptedAt());
        entity.setCompletedAt(domain.getCompletedAt());
        return entity;
    }

    public CompensationDlq toDomain(CompensationDlqEntity entity) {
        return CompensationDlq.reconstitute(
                entity.getId(),
                entity.getOrderId(),
                entity.getPaymentId(),
                entity.getFailureReason(),
                entity.getRetryCount(),
                entity.getMaxRetries(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLastAttemptedAt(),
                entity.getCompletedAt()
        );
    }
}
