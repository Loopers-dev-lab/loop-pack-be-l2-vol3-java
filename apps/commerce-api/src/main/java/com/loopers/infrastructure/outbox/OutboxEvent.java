package com.loopers.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    private OutboxEvent(String eventId, String topic, String partitionKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
    }

    public static OutboxEvent of(String eventId, String topic, String partitionKey, String payload) {
        return new OutboxEvent(eventId, topic, partitionKey, payload);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

    public String topic() {
        return topic;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String payload() {
        return payload;
    }

    public ZonedDateTime publishedAt() {
        return publishedAt;
    }

    public void markPublished() {
        this.publishedAt = ZonedDateTime.now();
    }
}
