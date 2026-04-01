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
 * 멱등성 처리 테이블 — 이미 처리한 이벤트를 기록 (인프라 전용)
 *
 * Consumer가 메시지 처리 시:
 * 1. event_id로 조회 → 이미 있으면 중복 스킵
 * 2. 비즈니스 로직 + INSERT를 같은 TX → 롤백 시 재처리 가능
 *
 * UNIQUE 제약 조건으로 동시 처리 방어 (DB 레벨 동시성 제어)
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

    protected EventHandledEntity() {
    }

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
