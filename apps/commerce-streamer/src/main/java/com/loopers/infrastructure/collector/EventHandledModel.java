package com.loopers.infrastructure.collector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class EventHandledModel {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_no")
    private Integer partitionNo;

    @Column(name = "offset_no")
    private Long offsetNo;

    @Column(name = "handled_at", nullable = false)
    private Instant handledAt;

    private EventHandledModel(String eventId, String topic, Integer partitionNo, Long offsetNo, Instant handledAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.handledAt = handledAt;
    }

    public static EventHandledModel of(String eventId, String topic, Integer partitionNo, Long offsetNo) {
        return new EventHandledModel(eventId, topic, partitionNo, offsetNo, Instant.now());
    }
}
