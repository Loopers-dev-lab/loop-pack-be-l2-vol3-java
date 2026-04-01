package com.loopers.infrastructure.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 멱등성 테이블 — Consumer가 이미 처리한 이벤트를 기록
 * UNIQUE(event_id)로 DB 레벨 동시성 방어
 */
@Entity
@Table(name = "event_handled", indexes = {
        @Index(name = "idx_event_handled_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_event_handled_topic_handled", columnList = "topic, handled_at")
})
public class EventHandledEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledEntity() {}

    public static EventHandledEntity of(String eventId, String topic) {
        EventHandledEntity entity = new EventHandledEntity();
        entity.eventId = eventId;
        entity.topic = topic;
        entity.handledAt = ZonedDateTime.now();
        return entity;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public ZonedDateTime getHandledAt() { return handledAt; }
}
