package com.loopers.domain.idempotency;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "event_handled", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class EventHandled extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    protected EventHandled() {}

    public EventHandled(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}