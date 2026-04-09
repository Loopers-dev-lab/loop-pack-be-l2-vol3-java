package com.loopers.application.metrics;

import com.loopers.application.idempotent.IdempotencyChecker;
import com.loopers.application.log.EventLogWriter;
import com.loopers.domain.metrics.ProductLikeMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InteractionMetricProcessor {

    private final IdempotencyChecker idempotencyChecker;
    private final ProductLikeMetricRepository likeMetricRepository;
    private final EventLogWriter eventLogWriter;
    private final ConsumerMetrics consumerMetrics;

    public void process(String eventId, String eventType, String topic, String groupId,
                        Long productId, Instant occurredAt) {
        long start = System.currentTimeMillis();

        if (!idempotencyChecker.tryMark(eventId, eventType)) {
            eventLogWriter.saveSkipped(eventId, eventType, topic, groupId);
            consumerMetrics.recordSkipped(topic, groupId, eventType);
            return;
        }

        try {
            LocalDateTime bucketTime = BucketTimeUtils.toLocalDateTime(BucketTimeUtils.truncate5min(occurredAt));
            int delta = "product.liked".equals(eventType) ? 1 : -1;
            likeMetricRepository.upsert(productId, bucketTime, delta);

            long durationMs = System.currentTimeMillis() - start;
            eventLogWriter.saveProcessed(eventId, eventType, topic, groupId, durationMs);
            consumerMetrics.recordProcessed(topic, groupId, eventType, durationMs);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            eventLogWriter.saveFailed(eventId, eventType, topic, groupId, e.getMessage(), durationMs);
            consumerMetrics.recordFailed(topic, groupId, eventType);
            throw e;
        }
    }
}
