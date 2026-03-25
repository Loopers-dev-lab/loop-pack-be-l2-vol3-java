package com.loopers.infrastructure.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_handled")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "handled_at", nullable = false, updatable = false)
    private ZonedDateTime handledAt;

    private EventHandled(String eventId) {
        this.eventId = eventId;
    }

    public static EventHandled of(String eventId) {
        return new EventHandled(eventId);
    }

    @PrePersist
    private void prePersist() {
        this.handledAt = ZonedDateTime.now();
    }
}
