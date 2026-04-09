package com.loopers.application.log;

import com.loopers.domain.log.EventLog;
import com.loopers.domain.log.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EventLogWriter {

    private final EventLogRepository eventLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProcessed(String eventId, String eventType, String topic, String groupId, long durationMs) {
        eventLogRepository.save(EventLog.processed(eventId, eventType, topic, groupId, durationMs));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSkipped(String eventId, String eventType, String topic, String groupId) {
        eventLogRepository.save(EventLog.skipped(eventId, eventType, topic, groupId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailed(String eventId, String eventType, String topic, String groupId,
                           String errorMessage, long durationMs) {
        eventLogRepository.save(EventLog.failed(eventId, eventType, topic, groupId, errorMessage, durationMs));
    }
}
