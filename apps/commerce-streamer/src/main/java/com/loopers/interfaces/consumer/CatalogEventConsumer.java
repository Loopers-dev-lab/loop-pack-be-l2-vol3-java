package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsApplicationService;
import com.loopers.application.ranking.RankingScoreService;
import com.loopers.config.kafka.KafkaConfig;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private static final String DLQ_TOPIC = "catalog-events.dlq";

    private final MetricsApplicationService metricsApplicationService;
    private final RankingScoreService rankingScoreService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @KafkaListener(
        topics = "catalog-events",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        Map<Long, Integer> viewCounts = new HashMap<>();
        Map<Long, Integer> likeCounts = new HashMap<>();
        Map<Long, Integer> unlikeCounts = new HashMap<>();

        try {
            for (ConsumerRecord<String, byte[]> record : records) {
                try {
                    JsonNode envelope = parseEnvelope(record.value());
                    String eventId = requireText(envelope, "eventId");
                    String eventType = requireText(envelope, "eventType");

                    if (eventHandledRepository.existsById(eventId)) {
                        log.debug("[CatalogEvent] 이미 처리된 이벤트 skip: eventId={}", eventId);
                        continue;
                    }

                    JsonNode data = envelope.get("data");
                    processEvent(eventId, eventType, data, viewCounts, likeCounts, unlikeCounts);
                    log.info("[CatalogEvent] 처리 완료: eventId={}, eventType={}", eventId, eventType);
                } catch (Exception e) {
                    log.error("[CatalogEvent] 처리 실패 → DLQ 전송: offset={}, error={}",
                        record.offset(), e.getMessage(), e);
                    sendToDlq(record);
                }
            }
            flushRankingScores(viewCounts, likeCounts, unlikeCounts);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[CatalogEvent] 배치 처리 중단 (DLQ 전송 실패). 전체 재배달 예정. error={}", e.getMessage());
        }
    }

    private void processEvent(String eventId, String eventType, JsonNode data,
                              Map<Long, Integer> viewCounts, Map<Long, Integer> likeCounts,
                              Map<Long, Integer> unlikeCounts) {
        Long productId = requireLong(data, "productId");

        switch (eventType) {
            case "LIKED" -> {
                metricsApplicationService.incrementLikeCount(eventId, productId);
                likeCounts.merge(productId, 1, Integer::sum);
            }
            case "UNLIKED" -> {
                metricsApplicationService.decrementLikeCount(eventId, productId);
                unlikeCounts.merge(productId, 1, Integer::sum);
            }
            case "PRODUCT_VIEWED" -> {
                metricsApplicationService.incrementViewCount(eventId, productId);
                viewCounts.merge(productId, 1, Integer::sum);
            }
            default -> log.warn("[CatalogEvent] 알 수 없는 이벤트 타입: {}", eventType);
        }
    }

    private void flushRankingScores(Map<Long, Integer> viewCounts, Map<Long, Integer> likeCounts,
                                    Map<Long, Integer> unlikeCounts) {
        if (!viewCounts.isEmpty()) {
            addRankingScoreSafely(() -> rankingScoreService.addViewScores(viewCounts));
        }
        if (!likeCounts.isEmpty()) {
            addRankingScoreSafely(() -> rankingScoreService.addLikeScores(likeCounts));
        }
        if (!unlikeCounts.isEmpty()) {
            addRankingScoreSafely(() -> rankingScoreService.subtractLikeScores(unlikeCounts));
        }
    }

    private void sendToDlq(ConsumerRecord<String, byte[]> record) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, record.key(), record.value())
                .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[CatalogEvent] DLQ 전송 실패. topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key(), e);
            throw new RuntimeException("DLQ 전송 실패 — 전체 배치 재배달 필요", e);
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("필수 필드 누락: " + field);
        }
        return value.asText();
    }

    private Long requireLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("필수 필드 누락: " + field);
        }
        return value.asLong();
    }

    private void addRankingScoreSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[CatalogEvent] 랭킹 점수 반영 실패 (무시): {}", e.getMessage());
        }
    }

    private JsonNode parseEnvelope(byte[] value) throws IOException {
        JsonNode node = objectMapper.readTree(value);
        if (node.isTextual()) {
            node = objectMapper.readTree(node.asText());
        }
        return node;
    }
}
