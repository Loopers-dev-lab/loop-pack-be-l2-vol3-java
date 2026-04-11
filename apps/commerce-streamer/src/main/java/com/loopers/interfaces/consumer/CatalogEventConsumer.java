package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsApp;
import com.loopers.application.ranking.RankingApp;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private static final String TOPIC = "catalog-events";

    private final ProductMetricsApp productMetricsApp;
    private final RankingApp rankingApp;
    private final ObjectMapper objectMapper;

    private static final java.util.Set<String> LIKE_EVENT_TYPES =
            java.util.Set.of("LikedEvent", "LikeRemovedEvent");

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-catalog",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                CatalogEventPayload payload = parse(record);
                if (LIKE_EVENT_TYPES.contains(payload.eventType())) {
                    boolean processed = productMetricsApp.applyLikeDelta(
                            payload.eventId(),
                            payload.productDbId(),
                            payload.delta(),
                            payload.likedAt()
                    );
                    if (processed) {
                        try {
                            rankingApp.applyLikeDelta(
                                    payload.productDbId(),
                                    payload.delta(),
                                    payload.likedAt().toLocalDate()
                            );
                        } catch (Exception e) {
                            log.warn("[RANKING_BEST_EFFORT] Like 랭킹 반영 실패 — productDbId={}", payload.productDbId(), e);
                        }
                    }
                } else {
                    log.warn("[CATALOG_EVENT] 미지원 eventType={}, offset={} — 건너뜀",
                            payload.eventType(), record.offset());
                }
            } catch (Exception e) {
                log.error("[CATALOG_EVENT_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new org.springframework.kafka.listener.BatchListenerFailedException(
                        "catalog-events processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private CatalogEventPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), CatalogEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize CatalogEventPayload", e);
        }
    }
}
