package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private ZonedDateTime processedAt;

    protected EventHandled() {}

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.processedAt = ZonedDateTime.now();
    }
}
