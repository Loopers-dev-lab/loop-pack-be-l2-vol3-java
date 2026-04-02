package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMetricsProcessor {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void process(String eventId, String eventType, Long productId, ZonedDateTime occurredAt) {
        // 1. 중복 이벤트 체크 (동일 Kafka 메시지 재수신 방지)
        if (eventHandledRepository.existsByEventIdAndEventType(eventId, eventType)) {
            log.info("중복 이벤트 건너뜀. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        // 2. 스탈(stale) 이벤트 체크 (더 최신 이벤트가 이미 처리된 경우 skip)
        String entityId = String.valueOf(productId);
        if (eventHandledRepository.existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(entityId, eventType, occurredAt)) {
            log.info("이미 최신 이벤트가 처리됨. 스탈 이벤트 건너뜀. entityId={}, eventType={}, occurredAt={}", entityId, eventType, occurredAt);
            return;
        }

        ensureMetricsExists(productId);

        switch (eventType) {
            case "PRODUCT_VIEWED" -> productMetricsRepository.incrementViewCount(productId);
            case "LIKED" -> productMetricsRepository.incrementLikeCount(productId);
            case "UNLIKED" -> productMetricsRepository.decrementLikeCount(productId);
            case "ORDER_CONFIRMED" -> productMetricsRepository.incrementSalesCount(productId);
            default -> log.warn("알 수 없는 이벤트 타입. eventType={}", eventType);
        }

        eventHandledRepository.save(EventHandled.create(eventId, eventType, entityId, occurredAt));
    }

    private void ensureMetricsExists(Long productId) {
        productMetricsRepository.findByProductId(productId)
            .orElseGet(() -> productMetricsRepository.save(ProductMetrics.create(productId)));
    }
}
