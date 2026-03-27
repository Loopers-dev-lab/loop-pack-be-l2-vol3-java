package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * Consumer 멱등성 체크포인트
 *
 * 메시지 처리 전 eventId 존재 여부를 확인한다.
 * 이미 존재하면 skip → 같은 메시지가 재전달돼도 중복 처리 없음.
 *
 * 로그 테이블과 다른 점:
 * - 이 테이블: 체크포인트 (지워도 무방, 멱등성 보장이 목적)
 * - 로그 테이블: 히스토리 (지우면 안 됨, 감사/추적이 목적)
 */
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {}

    public static EventHandled of(String eventId) {
        EventHandled e = new EventHandled();
        e.eventId = eventId;
        e.handledAt = ZonedDateTime.now();
        return e;
    }

    public String getEventId() { return eventId; }
}
