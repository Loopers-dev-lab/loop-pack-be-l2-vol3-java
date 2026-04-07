package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingService;
import com.loopers.domain.ranking.RankingWeight;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 카탈로그 이벤트 프로세서.
 *
 * <p>이벤트 타입에 따라 상품 지표(product_metrics)를 갱신한다.
 * 비핵심 지표이므로 Outbox/멱등 처리 없이 at-least-once로 처리한다.
 * (중복 수신 시 카운트가 약간 부풀려질 수 있으나, 주기적 보정 배치가 보완한다.)</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventProcessor {

    private final ProductMetricsService productMetricsService;
    private final RankingService rankingService;
    private final ConsumerMetrics consumerMetrics;
    private final ObjectMapper objectMapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public void process(ConsumerRecord<Object, Object> record) {
        Object value = record.value();

        Map<String, Object> message;
        if (value instanceof String str) {
            try {
                message = objectMapper.readValue(str, Map.class);
            } catch (Exception e) {
                log.warn("[CatalogProcessor] JSON 파싱 실패: {}", str, e);
                return;
            }
        } else if (value instanceof Map) {
            message = (Map<String, Object>) value;
        } else {
            log.warn("[CatalogProcessor] 예상치 못한 메시지 타입: {}", value != null ? value.getClass() : "null");
            return;
        }
        String eventType = (String) message.get("eventType");
        Number productIdNum = (Number) message.get("productId");

        if (eventType == null || productIdNum == null) {
            log.warn("[CatalogProcessor] eventType 또는 productId 누락: {}", message);
            return;
        }

        Long productId = productIdNum.longValue();
        LocalDateTime occurredAt = parseOccurredAt(message);
        Long userId = parseUserId(message);

        switch (eventType) {
            case "PRODUCT_VIEWED" -> {
                productMetricsService.incrementViewCount(productId);
                try {
                    rankingService.incrementScore(productId, RankingWeight.VIEW, occurredAt);
                } catch (Exception e) {
                    log.warn("[CatalogProcessor] 랭킹 적재 실패 — PRODUCT_VIEWED, productId={}", productId, e);
                }
            }
            case "PRODUCT_LIKED" -> {
                productMetricsService.incrementLikeCount(productId);
                try {
                    rankingService.incrementLikeScoreIfAbsent(productId, userId, RankingWeight.LIKE, occurredAt);
                } catch (Exception e) {
                    log.warn("[CatalogProcessor] 랭킹 적재 실패 — PRODUCT_LIKED, productId={}", productId, e);
                }
            }
            case "PRODUCT_UNLIKED" -> {
                productMetricsService.decrementLikeCount(productId);
                try {
                    rankingService.decrementLikeScoreIfPresent(productId, userId, RankingWeight.LIKE, occurredAt);
                } catch (Exception e) {
                    log.warn("[CatalogProcessor] 랭킹 적재 실패 — PRODUCT_UNLIKED, productId={}", productId, e);
                }
            }
            default -> log.warn("[CatalogProcessor] 알 수 없는 eventType: {}", eventType);
        }
    }

    private LocalDateTime parseOccurredAt(Map<String, Object> message) {
        try {
            String occurredAtStr = (String) message.get("occurredAt");
            return occurredAtStr != null ? LocalDateTime.parse(occurredAtStr) : LocalDateTime.now();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private Long parseUserId(Map<String, Object> message) {
        Number userIdNum = (Number) message.get("userId");
        return userIdNum != null ? userIdNum.longValue() : null;
    }
}
