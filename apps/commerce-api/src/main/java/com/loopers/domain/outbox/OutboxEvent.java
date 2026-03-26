package com.loopers.domain.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private OutboxEvent(String topic, String eventType, String aggregateId, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.occurredAt = ZonedDateTime.now();
    }

    public static OutboxEvent create(String topic, String eventType, String aggregateId, String payload) {
        return new OutboxEvent(topic, eventType, aggregateId, payload);
    }

    public void markAsSent() {
        this.status = OutboxStatus.SENT;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
