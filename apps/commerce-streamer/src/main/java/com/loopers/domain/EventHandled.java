package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "event_handled")
public class EventHandled {
    @Id
    private Long eventId;

    @Column(nullable = false)
    private LocalDateTime handledAt;

    protected EventHandled() {}

    public EventHandled(Long eventId, LocalDateTime handledAt) {
        this.eventId = eventId;
        this.handledAt = handledAt;
    }
}
