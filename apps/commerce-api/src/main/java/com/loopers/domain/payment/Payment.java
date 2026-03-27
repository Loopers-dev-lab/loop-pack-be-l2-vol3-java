package com.loopers.domain.payment;

import com.loopers.domain.common.Money;

import java.time.ZonedDateTime;

/**
 * 결제 도메인 엔티티.
 * 순수 Java 객체로 JPA/Spring 의존성 없음.
 */
public class Payment {

    private Long id;
    private Long orderId;
    private String transactionId;
    private Money amount;
    private PaymentStatus status;
    private String failReason;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    private Payment() {}

    /**
     * 새 결제 요청 생성.
     *
     * @param orderId 주문 ID
     * @param transactionId PG 트랜잭션 ID
     * @param amount 결제 금액
     * @return 생성된 Payment (PENDING 상태)
     */
    public static Payment create(Long orderId, String transactionId, Money amount) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.transactionId = transactionId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = ZonedDateTime.now();
        payment.updatedAt = ZonedDateTime.now();
        return payment;
    }

    /**
     * DB에서 복원.
     */
    public static Payment reconstitute(Long id, Long orderId, String transactionId,
            Money amount, PaymentStatus status, String failReason,
            ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = orderId;
        payment.transactionId = transactionId;
        payment.amount = amount;
        payment.status = status;
        payment.failReason = failReason;
        payment.createdAt = createdAt;
        payment.updatedAt = updatedAt;
        return payment;
    }

    /**
     * 결제 성공 처리.
     */
    public void markAsSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 결제 실패 처리.
     *
     * @param failReason 실패 사유
     */
    public void markAsFail(String failReason) {
        this.status = PaymentStatus.FAIL;
        this.failReason = failReason;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 재시도 대기 상태로 변경.
     * CircuitBreaker OPEN 또는 Timeout 등으로 인해 재시도가 필요한 경우.
     *
     * @param reason 재시도 사유
     */
    public void markAsPendingRetry(String reason) {
        this.status = PaymentStatus.PENDING_RETRY;
        this.failReason = reason;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 재시도 대기 상태로 결제 생성.
     * PG 호출 전에 먼저 저장해야 하는 경우 사용.
     *
     * @param orderId 주문 ID
     * @param amount 결제 금액
     * @param reason 대기 사유
     * @return 생성된 Payment (PENDING_RETRY 상태)
     */
    public static Payment createPendingRetry(Long orderId, Money amount, String reason) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.transactionId = null;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING_RETRY;
        payment.failReason = reason;
        payment.createdAt = ZonedDateTime.now();
        payment.updatedAt = ZonedDateTime.now();
        return payment;
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

    public Money getAmount() {
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

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFail() {
        return status == PaymentStatus.FAIL;
    }

    public boolean isPendingRetry() {
        return status == PaymentStatus.PENDING_RETRY;
    }

    public boolean needsSync() {
        return isPending() || isPendingRetry();
    }

    /**
     * PG 응답으로 결제 정보 동기화.
     *
     * @param transactionId PG 트랜잭션 ID
     * @param pgStatus PG 결제 상태 (APPROVED, REJECTED, FAILED, PENDING 등)
     * @param failureReason 실패 사유 (실패 시)
     */
    public void syncWithPgResponse(String transactionId, String pgStatus, String failureReason) {
        if (this.transactionId == null && transactionId != null) {
            this.transactionId = transactionId;
        }

        switch (pgStatus) {
            case "APPROVED" -> markAsSuccess();
            case "REJECTED", "FAILED" -> markAsFail(failureReason);
            case "CANCELLED" -> markAsFail("결제가 취소되었습니다.");
            // PENDING인 경우 상태 유지
        }
    }
}
