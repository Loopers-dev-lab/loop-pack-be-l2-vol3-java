package com.loopers.domain.idempotent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_handled")
@Getter
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {
    }

    private EventHandled(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.handledAt = ZonedDateTime.now();
    }

    public static EventHandled create(String eventId, String eventType) {
        return new EventHandled(eventId, eventType);
    }
}
