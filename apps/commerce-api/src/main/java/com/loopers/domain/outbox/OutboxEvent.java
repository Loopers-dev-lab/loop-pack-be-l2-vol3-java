package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "outbox_events")
@Getter
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "partition_key", length = 255)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    protected OutboxEvent() {}

    public OutboxEvent(String eventId, String topic, String partitionKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }
}
