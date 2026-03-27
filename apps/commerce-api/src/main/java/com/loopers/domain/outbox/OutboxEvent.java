package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    protected OutboxEvent() {}

    private OutboxEvent(String eventId, String eventType, String topic, String payload, OutboxStatus status, String partitionKey) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.status = status;
        this.partitionKey = partitionKey;
    }

    public static OutboxEvent create(String eventType, String topic, String payload, String partitionKey) {
        return new OutboxEvent(UUID.randomUUID().toString(), eventType, topic, payload, OutboxStatus.PENDING, partitionKey);
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public String getPartitionKey() {
        return partitionKey;
    }
}