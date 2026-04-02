package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

public class OutboxEvent {

    private final Long id;
    private final String eventType;
    private final String payload;
    private final String partitionKey;
    private final ZonedDateTime publishedAt;
    private final ZonedDateTime createdAt;

    private OutboxEvent(Long id, String eventType, String payload, String partitionKey, ZonedDateTime publishedAt, ZonedDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.partitionKey = partitionKey;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    public static OutboxEvent create(String eventType, String payload, String partitionKey) {
        return new OutboxEvent(null, eventType, payload, partitionKey, null, null);
    }

    public static OutboxEvent restore(Long id, String eventType, String payload, String partitionKey, ZonedDateTime publishedAt, ZonedDateTime createdAt) {
        return new OutboxEvent(id, eventType, payload, partitionKey, publishedAt, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
