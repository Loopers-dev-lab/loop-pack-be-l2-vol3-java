package com.loopers.domain.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 멱등 처리 전용 엔티티 (핫 테이블).
 * Consumer가 이벤트를 처리할 때 event_id를 기록하여 중복 처리를 방지한다.
 * 30일 후 배치 삭제 (Kafka retention 7일의 4배 이상 안전 마진).
 */
@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;

    public EventHandledModel(Long eventId) {
        this.eventId = eventId;
        this.handledAt = LocalDateTime.now();
    }
}
