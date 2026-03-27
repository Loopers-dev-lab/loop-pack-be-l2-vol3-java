package com.loopers.infrastructure.event.entity;

import com.loopers.domain.event.model.EventHandleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_handled")
public class EventHandledEntity {

    @Id
    @Column(length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventHandleStatus status;

    @Column(nullable = false)
    private LocalDateTime handledAt;

    public static EventHandledEntity of(String eventId, EventHandleStatus status) {
        EventHandledEntity entity = new EventHandledEntity();
        entity.eventId = eventId;
        entity.status = status;
        entity.handledAt = LocalDateTime.now();
        return entity;
    }
}
