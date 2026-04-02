package com.loopers.domain.eventhandled;

import java.time.Instant;
import java.util.Objects;

/**
 * 처리된 이벤트 기록.
 * 멱등성 보장을 위해 처리 완료된 이벤트 ID를 저장.
 */
public class EventHandled {

    private final Long id;
    private final String eventId;
    private final String eventType;
    private final Instant handledAt;

    private EventHandled(Long id, String eventId, String eventType, Instant handledAt) {
        this.id = id;
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.handledAt = handledAt;
    }

    public static EventHandled create(String eventId, String eventType) {
        return new EventHandled(null, eventId, eventType, Instant.now());
    }

    public static EventHandled reconstitute(Long id, String eventId, String eventType, Instant handledAt) {
        return new EventHandled(id, eventId, eventType, handledAt);
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
