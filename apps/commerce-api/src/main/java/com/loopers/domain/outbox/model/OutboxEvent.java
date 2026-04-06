package com.loopers.domain.outbox.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OutboxEvent {

    private Long id;
    private OutboxEventType eventType;
    private String aggregateId;
    private String payload;
    private OutboxStatus status;
    private LocalDateTime createdAt;

    private OutboxEvent(OutboxEventType eventType, String aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.createdAt = LocalDateTime.now();
    }

    public static OutboxEvent create(OutboxEventType eventType, String aggregateId, String payload) {
        return new OutboxEvent(eventType, aggregateId, payload);
    }

    public static OutboxEvent reconstruct(Long id, OutboxEventType eventType, String aggregateId,
                                          String payload, OutboxStatus status, LocalDateTime createdAt) {
        OutboxEvent event = new OutboxEvent(eventType, aggregateId, payload);
        event.id = id;
        event.status = status;
        event.createdAt = createdAt;
        return event;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }
}
