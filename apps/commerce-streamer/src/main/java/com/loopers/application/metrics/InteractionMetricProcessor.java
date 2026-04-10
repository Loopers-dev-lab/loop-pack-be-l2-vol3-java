package com.loopers.application.metrics;

import com.loopers.application.log.EventLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InteractionMetricProcessor {

    private final InteractionMetricWriter interactionMetricWriter;
    private final EventLogWriter eventLogWriter;
    private final ConsumerMetrics consumerMetrics;

    public void process(String eventId, String eventType, String topic, String groupId,
                        Long productId, Instant occurredAt) {
        long start = System.currentTimeMillis();

        try {
            boolean processed = interactionMetricWriter.write(eventId, eventType, productId, occurredAt);

            if (processed) {
                long durationMs = System.currentTimeMillis() - start;
                eventLogWriter.saveProcessed(eventId, eventType, topic, groupId, durationMs);
                consumerMetrics.recordProcessed(topic, groupId, eventType, durationMs);
            } else {
                eventLogWriter.saveSkipped(eventId, eventType, topic, groupId);
                consumerMetrics.recordSkipped(topic, groupId, eventType);
            }
        } catch (DataIntegrityViolationException e) {
            eventLogWriter.saveSkipped(eventId, eventType, topic, groupId);
            consumerMetrics.recordSkipped(topic, groupId, eventType);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            eventLogWriter.saveFailed(eventId, eventType, topic, groupId, e.getMessage(), durationMs);
            consumerMetrics.recordFailed(topic, groupId, eventType);
            throw e;
        }
    }
}
