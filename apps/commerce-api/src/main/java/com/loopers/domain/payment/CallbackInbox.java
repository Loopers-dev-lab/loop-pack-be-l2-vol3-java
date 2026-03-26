package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * PG 콜백 원본 저장소 (DLQ 역할).
 *
 * <p>수신된 콜백을 즉시 저장(RECEIVED)하고,
 * 비동기로 처리(PROCESSED/FAILED)한다.</p>
 *
 * @see <a href="05-payment-resilience.md §8.5">Callback Inbox DLQ</a>
 */
@Entity
@Table(name = "callback_inbox", indexes = {
    @Index(name = "idx_callback_inbox_transaction_key", columnList = "transaction_key"),
    @Index(name = "idx_callback_inbox_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallbackInbox extends BaseEntity {

    @Column(name = "transaction_key", nullable = false)
    private String transactionKey;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "pg_status", nullable = false)
    private String pgStatus;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallbackInboxStatus status;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    public static CallbackInbox create(String transactionKey, Long orderId,
                                        String pgStatus, String payload) {
        CallbackInbox inbox = new CallbackInbox();
        inbox.transactionKey = transactionKey;
        inbox.orderId = orderId;
        inbox.pgStatus = pgStatus;
        inbox.payload = payload;
        inbox.status = CallbackInboxStatus.RECEIVED;
        inbox.retryCount = 0;
        return inbox;
    }

    public void markProcessed() {
        this.status = CallbackInboxStatus.PROCESSED;
        this.processedAt = ZonedDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = CallbackInboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
}
