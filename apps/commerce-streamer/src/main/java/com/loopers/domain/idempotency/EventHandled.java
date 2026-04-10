package com.loopers.domain.idempotency;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {
    }

    public EventHandled(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.handledAt = ZonedDateTime.now();
    }
}
