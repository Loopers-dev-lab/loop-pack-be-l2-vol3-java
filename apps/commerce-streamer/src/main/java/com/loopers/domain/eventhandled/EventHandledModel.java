package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel {

    @Id
    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;

    private EventHandledModel(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.handledAt = LocalDateTime.now();
    }

    public static EventHandledModel create(String eventId, String topic) {
        return new EventHandledModel(eventId, topic);
    }
}
