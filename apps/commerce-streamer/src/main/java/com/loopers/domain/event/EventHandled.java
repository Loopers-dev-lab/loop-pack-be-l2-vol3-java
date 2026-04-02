package com.loopers.domain.event;

import java.time.ZonedDateTime;

public class EventHandled {

    private final Long id;
    private final String eventId;
    private final String eventType;
    private final String entityId;
    private final ZonedDateTime occurredAt;

    private EventHandled(Long id, String eventId, String eventType, String entityId, ZonedDateTime occurredAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.entityId = entityId;
        this.occurredAt = occurredAt;
    }

    public static EventHandled create(String eventId, String eventType, String entityId, ZonedDateTime occurredAt) {
        return new EventHandled(null, eventId, eventType, entityId, occurredAt);
    }

    public static EventHandled restore(Long id, String eventId, String eventType, String entityId, ZonedDateTime occurredAt) {
        return new EventHandled(id, eventId, eventType, entityId, occurredAt);
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getEntityId() { return entityId; }
    public ZonedDateTime getOccurredAt() { return occurredAt; }
}
