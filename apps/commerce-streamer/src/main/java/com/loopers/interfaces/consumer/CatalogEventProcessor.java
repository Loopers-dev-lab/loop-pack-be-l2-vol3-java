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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카탈로그 이벤트 프로세서.
 *
 * <p>이벤트 타입에 따라 상품 지표(product_metrics)를 갱신한다.
 * 비핵심 지표이므로 Outbox/멱등 처리 없이 at-least-once로 처리한다.
 * (중복 수신 시 카운트가 약간 부풀려질 수 있으나, 주기적 보정 배치가 보완한다.)</p>
 *
 * <p>PRODUCT_VIEWED 이벤트는 배치 합산 후 Redis Pipeline으로 일괄 처리하여
 * N건당 1 RTT로 처리한다 (기존: 1건당 4 RTT).</p>
 *
 * <p>{@code @Transactional} 미적용 — DB 쓰기는 ProductMetricsService가 자체
 * 트랜잭션을 보유하고, Redis 호출은 트랜잭션과 무관하므로 DB 커넥션 점유를 최소화한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventProcessor {

    private final ProductMetricsService productMetricsService;
    private final RankingService rankingService;
    private final ConsumerMetrics consumerMetrics;
    private final ObjectMapper objectMapper;

    /**
     * Kafka 배치 레코드를 처리한다.
     *
     * <p>VIEWED 이벤트는 productId별로 점수를 합산한 뒤 Redis Pipeline으로 일괄 처리.
     * LIKED/UNLIKED는 멱등 처리(SADD)가 필요하므로 1건씩 처리.</p>
     */
    public void processBatch(List<ConsumerRecord<Object, Object>> records) {
        Map<Long, Double> viewScores = new HashMap<>();
        Map<Long, Integer> viewCounts = new HashMap<>();
        List<ParsedEvent> likeEvents = new ArrayList<>();

        // Phase 1: 파싱 + VIEWED 합산
        for (ConsumerRecord<Object, Object> record : records) {
            ParsedEvent event = parseRecord(record);
            if (event == null) continue;

            switch (event.eventType) {
                case "PRODUCT_VIEWED" -> {
                    viewScores.merge(event.productId, RankingWeight.VIEW, Double::sum);
                    viewCounts.merge(event.productId, 1, Integer::sum);
                }
                case "PRODUCT_LIKED", "PRODUCT_UNLIKED" -> likeEvents.add(event);
                default -> log.warn("[CatalogProcessor] 알 수 없는 eventType: {}", event.eventType);
            }
        }

        // Phase 2: VIEWED — DB 배치 + Redis Pipeline
        if (!viewScores.isEmpty()) {
            // DB: 조회수 배치 합산 증가 (productId당 1회 upsert)
            viewCounts.forEach((productId, count) ->
                    productMetricsService.incrementViewCountBy(productId, count));

            // Redis: Pipeline 배치 처리
            try {
                rankingService.incrementScoreBatch(viewScores);
            } catch (Exception e) {
                log.warn("[CatalogProcessor] 랭킹 배치 적재 실패 — PRODUCT_VIEWED, products={}", viewScores.keySet(), e);
            }

            log.debug("[CatalogProcessor] VIEWED 배치 처리 완료 — {}개 상품, {}건 이벤트",
                    viewScores.size(), viewCounts.values().stream().mapToInt(i -> i).sum());
        }

        // Phase 3: LIKED/UNLIKED — 1건씩 처리 (멱등 보장)
        for (ParsedEvent event : likeEvents) {
            try {
                if ("PRODUCT_LIKED".equals(event.eventType)) {
                    productMetricsService.incrementLikeCount(event.productId);
                    rankingService.incrementLikeScoreIfAbsent(
                            event.productId, event.userId, RankingWeight.LIKE, event.occurredAt);
                } else {
                    productMetricsService.decrementLikeCount(event.productId);
                    rankingService.decrementLikeScoreIfPresent(
                            event.productId, event.userId, RankingWeight.LIKE, event.occurredAt);
                }
            } catch (Exception e) {
                log.warn("[CatalogProcessor] 처리 실패 — {}, productId={}",
                        event.eventType, event.productId, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ParsedEvent parseRecord(ConsumerRecord<Object, Object> record) {
        Object value = record.value();

        Map<String, Object> message;
        if (value instanceof String str) {
            try {
                message = objectMapper.readValue(str, Map.class);
            } catch (Exception e) {
                log.warn("[CatalogProcessor] JSON 파싱 실패: {}", str, e);
                return null;
            }
        } else if (value instanceof Map) {
            message = (Map<String, Object>) value;
        } else {
            log.warn("[CatalogProcessor] 예상치 못한 메시지 타입: {}",
                    value != null ? value.getClass() : "null");
            return null;
        }

        String eventType = (String) message.get("eventType");
        Number productIdNum = (Number) message.get("productId");

        if (eventType == null || productIdNum == null) {
            log.warn("[CatalogProcessor] eventType 또는 productId 누락: {}", message);
            return null;
        }

        return new ParsedEvent(
                eventType,
                productIdNum.longValue(),
                parseUserId(message),
                parseOccurredAt(message)
        );
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

    private record ParsedEvent(String eventType, Long productId, Long userId, LocalDateTime occurredAt) {}
}
