package com.loopers.application.metrics;

import com.loopers.application.metrics.OrderMetricProcessor.OrderItemMetric;
import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.metrics.ProductOrderMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMetricWriter {

    private final EventHandledRepository eventHandledRepository;
    private final ProductOrderMetricRepository orderMetricRepository;

    @Transactional
    public boolean write(String eventId, String eventType, Instant occurredAt, List<OrderItemMetric> items) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return false;
        }
        eventHandledRepository.save(EventHandled.create(eventId, eventType));

        LocalDateTime bucketTime = BucketTimeUtils.toLocalDateTime(BucketTimeUtils.truncate5min(occurredAt));
        for (OrderItemMetric item : items) {
            orderMetricRepository.upsert(
                    item.productId(), bucketTime,
                    1, item.quantity(), item.salesAmount()
            );
        }
        return true;
    }
}
