package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsEventService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void process(String topic, String eventId, String eventType, Long productId,
                        int quantity, BigDecimal price, Instant occurredAt) {
        if (eventHandledRepository.existsByTopicAndEventId(topic, eventId)) {
            log.info("이미 처리된 이벤트 skip - topic: {}, eventId: {}", topic, eventId);
            return;
        }

        eventHandledRepository.save(EventHandled.create(topic, eventId));

        switch (eventType) {
            case "PRODUCT_VIEWED" -> productMetricsRepository.upsertViewCount(productId, 1);
            case "PRODUCT_LIKED" -> productMetricsRepository.upsertLikeCount(productId, 1);
            case "PRODUCT_UNLIKED" -> productMetricsRepository.upsertLikeCount(productId, -1);
            case "ORDER_PLACED" -> productMetricsRepository.upsertSalesCount(productId, quantity);
            case "ORDER_CANCELLED" -> productMetricsRepository.upsertSalesCount(productId, -quantity);
            case "PRODUCT_PRICE_CHANGED" -> productMetricsRepository.upsertPrice(productId, price, occurredAt);
            default -> log.warn("알 수 없는 이벤트 타입 - eventType: {}", eventType);
        }
    }
}
