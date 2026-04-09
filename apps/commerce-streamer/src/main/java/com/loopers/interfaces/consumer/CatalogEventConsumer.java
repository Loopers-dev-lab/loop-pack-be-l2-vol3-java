package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogMetricEvent;
import com.loopers.application.metrics.ProductMetricsAppService;
import com.loopers.application.ranking.RankingAppService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {
    private final ProductMetricsAppService productMetricsAppService;
    private final RankingAppService rankingAppService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "catalog-events",
            groupId = "catalog-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> messages, Acknowledgment acknowledgment) {
        List<CatalogMetricEvent> events = new ArrayList<>(messages.size());
        for (ConsumerRecord<String, byte[]> record : messages) {
            try {
                // Producer uses JsonSerializer over String payload → bytes are JSON-quoted string.
                // Unwrap outer string, then parse inner JSON.
                String payload = objectMapper.readValue(record.value(), String.class);
                JsonNode node = objectMapper.readTree(payload);
                String eventId = record.topic() + ":" + record.partition() + ":" + record.offset();
                String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
                Long productId = node.get("productId").asLong();
                ZonedDateTime occurredAt = ZonedDateTime.parse(node.get("occurredAt").asText());

                switch (eventType) {
                    case "ProductViewed" -> events.add(new CatalogMetricEvent(
                            eventId, CatalogMetricEvent.Type.VIEWED, productId, false, occurredAt));
                    case "LikeToggled" -> {
                        boolean liked = node.get("liked").asBoolean();
                        events.add(new CatalogMetricEvent(
                                eventId, CatalogMetricEvent.Type.LIKED, productId, liked, occurredAt));
                    }
                    default -> log.warn("알 수 없는 catalog 이벤트: eventType={}", eventType);
                }
            } catch (JsonProcessingException e) {
                log.error("catalog-events 메시지 파싱 실패 (skip): {}", new String(record.value()), e);
            } catch (Exception e) {
                log.error("catalog-events 파싱 실패 (skip): {}", new String(record.value()), e);
            }
        }

        try {
            productMetricsAppService.handleCatalogEventBatch(events);
            // Redis hourly 는 DB 트랜잭션 밖에서 건별 처리
            for (CatalogMetricEvent e : events) {
                if (e.type() == CatalogMetricEvent.Type.VIEWED) {
                    rankingAppService.updateViewRanking(e.productId());
                } else if (e.liked()) {
                    rankingAppService.updateLikeRanking(e.productId());
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("catalog-events 배치 처리 실패 — Kafka 재전달 예정", ex);
            // ack 미호출 → Kafka 재전달, 멱등성 가드가 중복 방지
        }
    }
}
