package com.loopers.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Transactional Outbox Pattern — 발행 대기 이벤트 레코드.
 * Order INSERT와 동일 트랜잭션에서 저장, OutboxRelay가 Kafka 발행 후 published=true 마킹.
 */
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String topic, String partitionKey, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.published = false;
        this.createdAt = ZonedDateTime.now();
    }

    public void markPublished() {
        this.published = true;
    }

    public static OutboxEvent forOrderCreated(Long orderId, Long productId, int quantity, int finalAmount) {
        String payloadEventId = UUID.randomUUID().toString();
        String payload = String.format(
            "{\"type\":\"ORDER_CREATED\",\"orderId\":%d,\"productId\":%d,\"quantity\":%d,\"finalAmount\":%d,\"eventId\":\"%s\"}",
            orderId, productId, quantity, finalAmount, payloadEventId
        );
        return new OutboxEvent("order-events", String.valueOf(orderId), payload);
    }

    public static OutboxEvent forPaymentResult(Long orderId, String status) {
        String payloadEventId = UUID.randomUUID().toString();
        String payload = String.format(
            "{\"type\":\"PAYMENT_RESULT\",\"orderId\":%d,\"status\":\"%s\",\"eventId\":\"%s\"}",
            orderId, status, payloadEventId
        );
        return new OutboxEvent("payment-events", String.valueOf(orderId), payload);
    }
}
