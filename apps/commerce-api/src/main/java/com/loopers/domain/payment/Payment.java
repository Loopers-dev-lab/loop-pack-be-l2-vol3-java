package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class Payment {

    private final Long id;
    private final Long refOrderId;
    private PaymentStatus status;
    private String pgTransactionId;
    private String failReason;
    private final ZonedDateTime createdAt;

    private Payment(Long id, Long refOrderId, PaymentStatus status,
                    String pgTransactionId, String failReason, ZonedDateTime createdAt) {
        this.id = id;
        this.refOrderId = refOrderId;
        this.status = status;
        this.pgTransactionId = pgTransactionId;
        this.failReason = failReason;
        this.createdAt = createdAt;
    }

    public static Payment create(Long orderId) {
        validateOrderId(orderId);
        return new Payment(null, orderId, PaymentStatus.PENDING, null, null, ZonedDateTime.now());
    }

    public static Payment restore(Long id, Long refOrderId, PaymentStatus status,
                                   String pgTransactionId, String failReason, ZonedDateTime createdAt) {
        return new Payment(id, refOrderId, status, pgTransactionId, failReason, createdAt);
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public void markSuccess(String pgTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Payment.ALREADY_PROCESSED);
        }
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Payment.PG_TRANSACTION_ID_REQUIRED);
        }
        this.status = PaymentStatus.SUCCESS;
        this.pgTransactionId = pgTransactionId;
    }

    public void markFailed(String failReason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Payment.ALREADY_PROCESSED);
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Payment.ORDER_ID_INVALID);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRefOrderId() {
        return refOrderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public String getFailReason() {
        return failReason;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
