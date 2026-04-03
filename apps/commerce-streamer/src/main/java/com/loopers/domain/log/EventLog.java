package com.loopers.domain.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_log", indexes = {
        @Index(name = "idx_event_log_event_id", columnList = "event_id"),
        @Index(name = "idx_event_log_status_created", columnList = "status, created_at")
})
@Getter
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "group_id", nullable = false, length = 100)
    private String groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventLogStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected EventLog() {
    }

    private EventLog(String eventId, String eventType, String topic, String groupId,
                     EventLogStatus status, String errorMessage, long durationMs) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.groupId = groupId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.createdAt = ZonedDateTime.now();
    }

    public static EventLog processed(String eventId, String eventType, String topic, String groupId, long durationMs) {
        return new EventLog(eventId, eventType, topic, groupId, EventLogStatus.PROCESSED, null, durationMs);
    }

    public static EventLog skipped(String eventId, String eventType, String topic, String groupId) {
        return new EventLog(eventId, eventType, topic, groupId, EventLogStatus.SKIPPED, null, 0);
    }

    public static EventLog failed(String eventId, String eventType, String topic, String groupId, String errorMessage, long durationMs) {
        String truncated = errorMessage != null && errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
        return new EventLog(eventId, eventType, topic, groupId, EventLogStatus.FAILED, truncated, durationMs);
    }
}
