package com.loopers.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledEntity {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    public EventHandledEntity(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }
}
