package com.loopers.domain.outbox;

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
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {}

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }
}
