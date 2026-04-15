package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.infrastructure.dlq.DlqPublisher;
import com.loopers.infrastructure.ranking.RankingEventEntity;
import com.loopers.infrastructure.ranking.RankingEventJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 Consumer — 원천 이벤트 적재 + Redis ZSET 배치 flush
 *
 * 케브 피드백 기반 Consumer 분리:
 *   CatalogMetricsConsumer(catalog-metrics-group)에서 랭킹 책임을 분리.
 *   별도 consumer group(ranking-group)으로 같은 토픽을 독립 소비한다.
 *
 * 두 가지 책임 (같은 도메인 — 랭킹):
 *   1. 원천 이벤트를 ranking_event 테이블에 적재 (재계산용 SOT)
 *   2. 배치 합산 → Redis ZSET 일괄 적재 (실시간 랭킹용)
 *
 * 원천 데이터 적재 근거 (케브: "내일부터는 없다"):
 *   - 가중치 변경 시 재계산 가능
 *   - A/B 테스트 가능
 *   - 10주차 주간/월간 집계의 입력 데이터
 *
 * 멱등성: ranking_event 테이블의 (outbox_id, product_id) 복합 UNIQUE로 보장.
 *   INSERT 성공 = 신규 이벤트 → delta 수집
 *   INSERT 실패(UNIQUE 위반) = 중복 → skip, delta 미수집
 *
 * 트랜잭션 경계:
 *   - consume() 메서드에 @Transactional 없음 — 배치 전체를 하나의 TX로 묶지 않음
 *   - 개별 save()는 SimpleJpaRepository.save()의 자체 @Transactional로 실행
 *   - DataIntegrityViolationException은 해당 save의 TX 내에서 발생 → 해당 TX만 롤백
 *   - flushBatch()는 DB TX 밖에서 실행 → Redis 실패가 DB 적재에 영향 없음
 */
@Component
public class RankingConsumer {

    private static final Logger log = LoggerFactory.getLogger(RankingConsumer.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final RankingEventJpaRepository rankingEventRepository;
    private final RankingScoreUpdater rankingScoreUpdater;
    private final DlqPublisher dlqPublisher;

    public RankingConsumer(ObjectMapper objectMapper,
                           RankingEventJpaRepository rankingEventRepository,
                           RankingScoreUpdater rankingScoreUpdater,
                           DlqPublisher dlqPublisher) {
        this.objectMapper = objectMapper;
        this.rankingEventRepository = rankingEventRepository;
        this.rankingScoreUpdater = rankingScoreUpdater;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(
            topics = "catalog-events-v1",
            groupId = "ranking-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        Map<Long, Double> batchScores = new HashMap<>();

        for (ConsumerRecord<Object, Object> record : records) {
            try {
                String eventType = getHeader(record, "X-Event-Type");
                String outboxId = getHeader(record, "X-Outbox-Id");
                String payload = record.value().toString();

                if (eventType == null) {
                    log.warn("[Ranking] X-Event-Type 헤더 없음 — partition={}, offset={}",
                            record.partition(), record.offset());
                    continue;
                }

                // outboxId가 없는 이벤트(ProductViewedEvent: fire-and-forget)는
                // partition:offset을 synthetic ID로 사용
                String idempotencyKey = outboxId != null
                        ? outboxId
                        : "p" + record.partition() + "o" + record.offset();

                ZonedDateTime eventTime = Instant.ofEpochMilli(record.timestamp())
                        .atZone(KST);

                processEvent(eventType, idempotencyKey, payload, eventTime, batchScores);

            } catch (Exception e) {
                log.error("[Ranking] 처리 실패 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e);
            }
        }

        // 배치 합산된 점수를 일간 + 시간 키에 일괄 적재
        if (!batchScores.isEmpty()) {
            rankingScoreUpdater.flushBatch(batchScores);
        }

        ack.acknowledge();
    }

    /**
     * 이벤트를 원천 적재하고 랭킹 delta를 수집한다.
     *
     * 이벤트 타입별 처리:
     * - ProductViewedEvent, ProductLikedEvent, ProductUnlikedEvent: productId 1개 → 1행 적재
     * - OrderItemSoldEvent: productQtyMap의 상품별 → N행 적재 (1 row = 1 상품 × 1 주문)
     */
    private void processEvent(String eventType, String idempotencyKey, String payload,
                               ZonedDateTime eventTime, Map<Long, Double> batchScores) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("[Ranking] JSON 파싱 실패 — payload={}", payload, e);
            return;
        }

        // 랭킹에 영향 없는 이벤트는 적재하지 않음
        String rankingEventType = toRankingEventType(eventType);
        if (rankingEventType == null) {
            return;
        }

        double delta = rankingScoreUpdater.calculateDelta(eventType);

        switch (eventType) {
            case "ProductViewedEvent", "ProductLikedEvent", "ProductUnlikedEvent" -> {
                long productId = node.path("productId").asLong(0);
                if (productId > 0) {
                    saveAndCollectDelta(idempotencyKey, productId, rankingEventType,
                            eventTime, delta, batchScores);
                }
            }
            case "OrderItemSoldEvent" -> {
                JsonNode productQtyMap = node.path("productQtyMap");
                if (productQtyMap.isMissingNode() || !productQtyMap.isObject()) {
                    return;
                }
                productQtyMap.fieldNames().forEachRemaining(key -> {
                    long productId = Long.parseLong(key);
                    saveAndCollectDelta(idempotencyKey, productId, rankingEventType,
                            eventTime, delta, batchScores);
                });
            }
        }
    }

    /**
     * 원천 이벤트를 적재하고, 성공 시 delta를 수집한다.
     *
     * INSERT 성공 = 신규 이벤트 → delta 수집
     * INSERT 실패(DataIntegrityViolationException) = 중복 → skip
     */
    private void saveAndCollectDelta(String outboxId, long productId, String eventType,
                                      ZonedDateTime eventTime, double delta,
                                      Map<Long, Double> batchScores) {
        RankingEventEntity event = RankingEventEntity.of(outboxId, productId, eventType, eventTime);

        try {
            rankingEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Ranking] 중복 스킵 — outboxId={}, productId={}", outboxId, productId);
            return;
        }

        // INSERT 성공 = 신규 이벤트 → delta 수집
        if (delta != 0.0) {
            batchScores.merge(productId, delta, Double::sum);
        }
    }

    /**
     * Kafka eventType → ranking_event.event_type 매핑
     *
     * 사실(fact)만 저장: "ProductViewedEvent" → "VIEW"
     */
    private String toRankingEventType(String eventType) {
        return switch (eventType) {
            case "ProductViewedEvent" -> "VIEW";
            case "ProductLikedEvent" -> "LIKE";
            case "ProductUnlikedEvent" -> "UNLIKE";
            case "OrderItemSoldEvent" -> "ORDER";
            default -> null;
        };
    }

    private String getHeader(ConsumerRecord<Object, Object> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
