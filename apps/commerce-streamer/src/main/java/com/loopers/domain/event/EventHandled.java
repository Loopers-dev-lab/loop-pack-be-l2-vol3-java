package com.loopers.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_handled", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "occurred_at")
    private ZonedDateTime occurredAt;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    private EventHandled(String eventId, ZonedDateTime occurredAt) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.handledAt = ZonedDateTime.now();
    }

    public static EventHandled create(String eventId) {
        return new EventHandled(eventId, null);
    }

    public static EventHandled create(String eventId, ZonedDateTime occurredAt) {
        return new EventHandled(eventId, occurredAt);
    }
}
