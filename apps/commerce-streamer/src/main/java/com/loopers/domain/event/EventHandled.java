package com.loopers.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 컨슈머 멱등성 보장을 위한 이벤트 처리 기록.
 *
 * event_id(outbox_events.id)를 UNIQUE로 관리하여 동일 이벤트 중복 처리를 방지한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    public EventHandled(Long eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    @PrePersist
    private void prePersist() {
        this.handledAt = ZonedDateTime.now();
    }
}
