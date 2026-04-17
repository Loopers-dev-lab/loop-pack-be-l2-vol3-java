package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingRepository;
import java.time.LocalDate;
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
    private final RankingRepository rankingRepository;

    @Transactional
    public void process(String eventId, String eventType, Long productId, Integer quantity, ZonedDateTime occurredAt) {
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

        LocalDate metricsDate = occurredAt.toLocalDate();
        ensureMetricsExists(productId, metricsDate);

        switch (eventType) {
            case "PRODUCT_VIEWED" -> {
                productMetricsRepository.incrementViewCount(productId, metricsDate);
                rankingRepository.incrementScore(productId, 0.1, metricsDate);
            }
            case "LIKED" -> {
                productMetricsRepository.incrementLikeCount(productId, metricsDate);
                rankingRepository.incrementScore(productId, 0.2, metricsDate);
            }
            case "UNLIKED" -> {
                productMetricsRepository.decrementLikeCount(productId, metricsDate);
                rankingRepository.incrementScore(productId, -0.2, metricsDate);
            }
            case "ORDER_CONFIRMED" -> {
                productMetricsRepository.incrementSalesAndQuantity(productId, metricsDate, quantity);
                rankingRepository.incrementScore(productId, 0.7 * Math.log1p(quantity), metricsDate);
            }
            default -> log.warn("알 수 없는 이벤트 타입. eventType={}", eventType);
        }

        eventHandledRepository.save(EventHandled.create(eventId, eventType, entityId, occurredAt));
    }

    private void ensureMetricsExists(Long productId, LocalDate metricsDate) {
        productMetricsRepository.upsertIfAbsent(productId, metricsDate);
    }
}
