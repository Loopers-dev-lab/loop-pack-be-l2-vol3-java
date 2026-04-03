package com.loopers.application.idempotent;

import com.loopers.application.metrics.ConsumerMetrics;
import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.log.EventLog;
import com.loopers.domain.log.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentProcessor {

    private final EventHandledRepository eventHandledRepository;
    private final EventLogRepository eventLogRepository;
    private final ConsumerMetrics consumerMetrics;

    @Transactional
    public boolean process(String eventId, String eventType, String topic, String groupId, Runnable handler) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.debug("이미 처리된 이벤트 스킵: eventId={}, topic={}, groupId={}", eventId, topic, groupId);
            eventLogRepository.save(EventLog.skipped(eventId, eventType, topic, groupId));
            consumerMetrics.recordSkipped(topic, groupId, eventType);
            return false;
        }

        long startTime = System.currentTimeMillis();
        try {
            handler.run();
            eventHandledRepository.save(EventHandled.create(eventId, eventType));

            long durationMs = System.currentTimeMillis() - startTime;
            eventLogRepository.save(EventLog.processed(eventId, eventType, topic, groupId, durationMs));
            consumerMetrics.recordProcessed(topic, groupId, eventType, durationMs);
            return true;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            eventLogRepository.save(EventLog.failed(eventId, eventType, topic, groupId, e.getMessage(), durationMs));
            consumerMetrics.recordFailed(topic, groupId, eventType);
            throw e;
        }
    }
}
