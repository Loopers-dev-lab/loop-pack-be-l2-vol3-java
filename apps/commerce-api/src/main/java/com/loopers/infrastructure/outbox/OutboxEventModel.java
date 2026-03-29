package com.loopers.infrastructure.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "idx_outbox_event_published_id", columnList = "published, id"),
        @Index(name = "idx_outbox_published_published_at", columnList = "published, published_at")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class OutboxEventModel extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 200)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    private OutboxEventModel(String eventId, String topic, String partitionKey, String eventType, Instant occurredAt,
            String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.published = false;
    }

    public static OutboxEventModel pending(String eventId, String topic, String partitionKey, String eventType,
            Instant occurredAt, String payload) {
        return new OutboxEventModel(eventId, topic, partitionKey, eventType, occurredAt, payload);
    }

    public void markPublished(Instant publishedAt) {
        this.published = true;
        this.publishedAt = publishedAt;
    }
}

