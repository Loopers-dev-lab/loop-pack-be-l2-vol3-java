package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel implements Persistable<String> {

    @Id
    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    @Transient
    private boolean isNew = false;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;

    private EventHandledModel(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.handledAt = LocalDateTime.now();
        this.isNew = true;
    }

    public static EventHandledModel create(String eventId, String topic) {
        return new EventHandledModel(eventId, topic);
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
