package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "requested_amount", nullable = false))
    private Money requestedAmount;

    @Column(name = "approved_amount")
    private Integer approvedAmount;

    @Column(name = "pg_txn_id")
    private String pgTxnId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    @Column(name = "failed_at")
    private ZonedDateTime failedAt;

    @Column(name = "canceled_at")
    private ZonedDateTime canceledAt;

    protected Payment() {}

    private Payment(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        this.orderId = orderId;
        this.requestedAmount = new Money(requestedAmount);
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.REQUESTED;
        this.requestedAt = ZonedDateTime.now();
    }

    public static Payment create(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        return new Payment(orderId, requestedAmount, paymentMethod, idempotencyKey);
    }

    public void approve(String pgTxnId, int approvedAmount) {
        this.status = PaymentStatus.APPROVED;
        this.pgTxnId = pgTxnId;
        this.approvedAmount = approvedAmount;
        this.approvedAt = ZonedDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.failedAt = ZonedDateTime.now();
    }

    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new CoreException(PaymentErrorType.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = ZonedDateTime.now();
    }

    public Long getOrderId() {
        return this.orderId;
    }

    public PaymentStatus getStatus() {
        return this.status;
    }

    public String getPaymentMethod() {
        return this.paymentMethod;
    }

    public int getRequestedAmount() {
        return this.requestedAmount.toInt();
    }

    public Integer getApprovedAmount() {
        return this.approvedAmount;
    }

    public String getPgTxnId() {
        return this.pgTxnId;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public ZonedDateTime getRequestedAt() {
        return this.requestedAt;
    }

    public ZonedDateTime getApprovedAt() {
        return this.approvedAt;
    }

    public ZonedDateTime getFailedAt() {
        return this.failedAt;
    }

    public ZonedDateTime getCanceledAt() {
        return this.canceledAt;
    }
}
