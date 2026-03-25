package com.loopers.domain.payment;

import java.time.ZonedDateTime;

/**
 * 보상 트랜잭션 DLQ (Dead Letter Queue)
 *
 * 보상 실패 시 DB에 기록하고, 대사 배치에서 재시도한다.
 * 최대 3회 재시도 후에도 실패하면 FAILED 상태로 전환 — 수동 개입 필요.
 */
public class CompensationDlq {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private Long id;
    private Long orderId;
    private Long paymentId;
    private String failureReason;
    private int retryCount;
    private int maxRetries;
    private CompensationDlqStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime lastAttemptedAt;
    private ZonedDateTime completedAt;

    private CompensationDlq() {}

    public static CompensationDlq create(Long orderId, Long paymentId, String failureReason) {
        CompensationDlq dlq = new CompensationDlq();
        dlq.orderId = orderId;
        dlq.paymentId = paymentId;
        dlq.failureReason = failureReason;
        dlq.retryCount = 0;
        dlq.maxRetries = DEFAULT_MAX_RETRIES;
        dlq.status = CompensationDlqStatus.PENDING;
        dlq.createdAt = ZonedDateTime.now();
        return dlq;
    }

    public static CompensationDlq reconstitute(Long id, Long orderId, Long paymentId, String failureReason,
                                                 int retryCount, int maxRetries, CompensationDlqStatus status,
                                                 ZonedDateTime createdAt, ZonedDateTime lastAttemptedAt,
                                                 ZonedDateTime completedAt) {
        CompensationDlq dlq = new CompensationDlq();
        dlq.id = id;
        dlq.orderId = orderId;
        dlq.paymentId = paymentId;
        dlq.failureReason = failureReason;
        dlq.retryCount = retryCount;
        dlq.maxRetries = maxRetries;
        dlq.status = status;
        dlq.createdAt = createdAt;
        dlq.lastAttemptedAt = lastAttemptedAt;
        dlq.completedAt = completedAt;
        return dlq;
    }

    public void markRetried(String newFailureReason) {
        this.retryCount++;
        this.lastAttemptedAt = ZonedDateTime.now();
        this.failureReason = newFailureReason;

        if (this.retryCount >= this.maxRetries) {
            this.status = CompensationDlqStatus.FAILED;
        }
    }

    public void markCompleted() {
        this.status = CompensationDlqStatus.COMPLETED;
        this.completedAt = ZonedDateTime.now();
        this.lastAttemptedAt = ZonedDateTime.now();
    }

    public boolean isRetryable() {
        return this.status == CompensationDlqStatus.PENDING && this.retryCount < this.maxRetries;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public CompensationDlqStatus getStatus() { return status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getLastAttemptedAt() { return lastAttemptedAt; }
    public ZonedDateTime getCompletedAt() { return completedAt; }
}
