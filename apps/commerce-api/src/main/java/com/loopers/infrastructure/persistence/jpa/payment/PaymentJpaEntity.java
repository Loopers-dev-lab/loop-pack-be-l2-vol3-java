package com.loopers.infrastructure.persistence.jpa.payment;

import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 결제 JPA 엔티티.
 * Infrastructure Layer에 위치하며 영속성을 담당.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id"),
        @Index(name = "idx_payments_transaction_id", columnList = "transaction_id", unique = true)
    }
)
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected PaymentJpaEntity() {}

    public PaymentJpaEntity(Long orderId, String transactionId, Long amount, PaymentStatus status) {
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
