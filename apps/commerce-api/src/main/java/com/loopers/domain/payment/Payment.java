package com.loopers.domain.payment;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import java.time.ZonedDateTime;

/**
 * Payment Aggregate Root (순수 POJO)
 * JPA 어노테이션 없음
 */
public class Payment {

    private Long id;
    private Long orderId;
    private PaymentStatus status;
    private String paymentMethod;
    private Money requestedAmount;
    private Integer approvedAmount;
    private String pgTxnId;
    private String idempotencyKey;
    private ZonedDateTime requestedAt;
    private ZonedDateTime approvedAt;
    private ZonedDateTime failedAt;
    private ZonedDateTime canceledAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected Payment() {}

    private Payment(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        this.orderId = orderId;
        this.requestedAmount = new Money(requestedAmount);
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.REQUESTED;
        this.requestedAt = ZonedDateTime.now();
    }

    public static Payment request(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        return new Payment(orderId, requestedAmount, paymentMethod, idempotencyKey);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static Payment reconstitute(Long id, Long orderId, PaymentStatus status, String paymentMethod,
                                        Money requestedAmount, Integer approvedAmount, String pgTxnId,
                                        String idempotencyKey, ZonedDateTime requestedAt, ZonedDateTime approvedAt,
                                        ZonedDateTime failedAt, ZonedDateTime canceledAt,
                                        ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = orderId;
        payment.status = status;
        payment.paymentMethod = paymentMethod;
        payment.requestedAmount = requestedAmount;
        payment.approvedAmount = approvedAmount;
        payment.pgTxnId = pgTxnId;
        payment.idempotencyKey = idempotencyKey;
        payment.requestedAt = requestedAt;
        payment.approvedAt = approvedAt;
        payment.failedAt = failedAt;
        payment.canceledAt = canceledAt;
        payment.createdAt = createdAt;
        payment.updatedAt = updatedAt;
        payment.deletedAt = deletedAt;
        return payment;
    }

    public void approve(String pgTxnId, int approvedAmount) {
        this.status = PaymentStatus.APPROVED;
        this.pgTxnId = pgTxnId;
        this.approvedAmount = approvedAmount;
        this.approvedAt = ZonedDateTime.now();
    }

    public void reject() {
        this.status = PaymentStatus.FAILED;
        this.failedAt = ZonedDateTime.now();
    }

    public void markUnknown() {
        this.status = PaymentStatus.UNKNOWN;
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

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }

    public Long getId() {
        return this.id;
    }
}
