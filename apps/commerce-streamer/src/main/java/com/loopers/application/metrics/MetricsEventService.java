package com.loopers.application.metrics;

import com.loopers.application.ranking.RankingScoreService;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsEventService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingScoreService rankingScoreService;

    @Transactional
    public void process(String topic, String eventId, String eventType, Long productId,
                        int quantity, BigDecimal price, Instant occurredAt) {
        if (eventHandledRepository.existsByTopicAndEventId(topic, eventId)) {
            log.info("이미 처리된 이벤트 skip - topic: {}, eventId: {}", topic, eventId);
            return;
        }

        eventHandledRepository.save(EventHandled.create(topic, eventId));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        switch (eventType) {
            case "PRODUCT_VIEWED" -> productMetricsRepository.upsertViewCount(productId, today, 1);
            case "PRODUCT_LIKED" -> productMetricsRepository.upsertLikeCount(productId, today, 1);
            case "PRODUCT_UNLIKED" -> productMetricsRepository.upsertLikeCount(productId, today, -1);
            case "ORDER_PLACED" -> productMetricsRepository.upsertSalesCount(productId, today, quantity);
            case "ORDER_CANCELLED" -> productMetricsRepository.upsertSalesCount(productId, today, -quantity);
            case "PRODUCT_PRICE_CHANGED" -> productMetricsRepository.upsertPrice(productId, today, price, occurredAt);
            default -> log.warn("알 수 없는 이벤트 타입 - eventType: {}", eventType);
        }

        rankingScoreService.updateScore(eventType, productId, quantity);
    }
}
