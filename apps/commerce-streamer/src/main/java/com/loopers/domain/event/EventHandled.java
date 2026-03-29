package com.loopers.domain.event;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_handled_topic_event_id", columnNames = {"topic", "event_id"})
    }
)
@Getter
public class EventHandled {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected EventHandled() {
    }

    public static EventHandled create(String topic, String eventId) {
        EventHandled handled = new EventHandled();
        handled.topic = topic;
        handled.eventId = eventId;
        handled.createdAt = ZonedDateTime.now();
        return handled;
    }
}
