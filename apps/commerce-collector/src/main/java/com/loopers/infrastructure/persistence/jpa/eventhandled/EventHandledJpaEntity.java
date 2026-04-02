package com.loopers.infrastructure.persistence.jpa.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 처리된 이벤트 기록 JPA 엔티티.
 */
@Entity
@Table(
    name = "event_handled",
    indexes = {
        @Index(name = "idx_event_handled_event_id", columnList = "event_id", unique = true)
    }
)
public class EventHandledJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private Instant handledAt;

    protected EventHandledJpaEntity() {}

    public EventHandledJpaEntity(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    @PrePersist
    private void prePersist() {
        this.handledAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
