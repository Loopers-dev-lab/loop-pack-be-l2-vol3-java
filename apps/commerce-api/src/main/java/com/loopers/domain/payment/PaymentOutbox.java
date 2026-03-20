package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Outbox 패턴 — Payment 생성과 같은 TX에서 저장.
 *
 * <p>"PG를 호출해야 한다"는 명시적 의도를 보존.
 * TX-1 커밋 후 서버 크래시 → Outbox 폴러가 5초 내 감지하여 재시도.</p>
 *
 * @see <a href="05-payment-resilience.md §13">Outbox 패턴</a>
 */
@Entity
@Table(name = "payment_outbox", indexes = {
    @Index(name = "idx_payment_outbox_status", columnList = "status"),
    @Index(name = "idx_payment_outbox_payment_id", columnList = "payment_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOutboxStatus status;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    public static PaymentOutbox create(Long paymentId, Long orderId, String payload) {
        PaymentOutbox outbox = new PaymentOutbox();
        outbox.paymentId = paymentId;
        outbox.orderId = orderId;
        outbox.eventType = "PAYMENT_REQUEST";
        outbox.payload = payload;
        outbox.status = PaymentOutboxStatus.PENDING;
        outbox.retryCount = 0;
        return outbox;
    }

    public void markProcessed() {
        this.status = PaymentOutboxStatus.PROCESSED;
        this.processedAt = ZonedDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentOutboxStatus.FAILED;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
