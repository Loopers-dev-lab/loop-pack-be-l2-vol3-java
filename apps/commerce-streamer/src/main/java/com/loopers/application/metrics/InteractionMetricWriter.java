package com.loopers.application.metrics;

import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.metrics.ProductLikeMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InteractionMetricWriter {

    private final EventHandledRepository eventHandledRepository;
    private final ProductLikeMetricRepository likeMetricRepository;

    @Transactional
    public boolean write(String eventId, String eventType, Long productId, Instant occurredAt) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return false;
        }
        eventHandledRepository.save(EventHandled.create(eventId, eventType));

        LocalDateTime bucketTime = BucketTimeUtils.toLocalDateTime(BucketTimeUtils.truncate5min(occurredAt));
        int delta = "product.liked".equals(eventType) ? 1 : -1;
        likeMetricRepository.upsert(productId, bucketTime, delta);
        return true;
    }
}
