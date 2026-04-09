package com.loopers.application.metrics;

import com.loopers.application.idempotent.IdempotencyChecker;
import com.loopers.application.log.EventLogWriter;
import com.loopers.domain.metrics.ProductOrderMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMetricProcessor {

    private final IdempotencyChecker idempotencyChecker;
    private final ProductOrderMetricRepository orderMetricRepository;
    private final EventLogWriter eventLogWriter;
    private final ConsumerMetrics consumerMetrics;

    public void process(String eventId, String eventType, String topic, String groupId,
                        Instant occurredAt, List<OrderItemMetric> items) {
        long start = System.currentTimeMillis();

        if (!idempotencyChecker.tryMark(eventId, eventType)) {
            eventLogWriter.saveSkipped(eventId, eventType, topic, groupId);
            consumerMetrics.recordSkipped(topic, groupId, eventType);
            return;
        }

        try {
            LocalDateTime bucketTime = BucketTimeUtils.toLocalDateTime(BucketTimeUtils.truncate5min(occurredAt));
            for (OrderItemMetric item : items) {
                orderMetricRepository.upsert(
                        item.productId(), bucketTime,
                        1, item.quantity(), item.salesAmount()
                );
            }

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

    public record OrderItemMetric(Long productId, int quantity, long salesAmount) {
    }
}
