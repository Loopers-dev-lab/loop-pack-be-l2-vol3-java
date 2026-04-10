package com.loopers.domain.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이벤트 처리 이력/감사 엔티티 (콜드 테이블).
 * 정상 처리, 멱등 skip, 실패 등 모든 처리 결과를 기록한다.
 * 90일 후 배치 삭제 또는 파티션 DROP.
 */
@Entity
@Table(name = "event_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventLogStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;

    public static EventLogModel success(Long eventId, String eventType, String topic) {
        EventLogModel log = new EventLogModel();
        log.eventId = eventId;
        log.eventType = eventType;
        log.topic = topic;
        log.status = EventLogStatus.SUCCESS;
        log.handledAt = LocalDateTime.now();
        return log;
    }

    public static EventLogModel skipped(Long eventId, String eventType, String topic) {
        EventLogModel log = new EventLogModel();
        log.eventId = eventId;
        log.eventType = eventType;
        log.topic = topic;
        log.status = EventLogStatus.SKIPPED;
        log.handledAt = LocalDateTime.now();
        return log;
    }

    public static EventLogModel failed(Long eventId, String eventType, String topic, String error) {
        EventLogModel log = new EventLogModel();
        log.eventId = eventId;
        log.eventType = eventType;
        log.topic = topic;
        log.status = EventLogStatus.FAILED;
        log.errorMessage = error;
        log.handledAt = LocalDateTime.now();
        return log;
    }
}
