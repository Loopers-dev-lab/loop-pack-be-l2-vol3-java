package com.loopers.domain.collector;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "consumer_group"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    public EventHandledModel(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
    }
}
