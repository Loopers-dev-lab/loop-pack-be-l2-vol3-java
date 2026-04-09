package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.OrderMetricEvent;
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
public class OrderEventConsumer {
    private final ProductMetricsAppService productMetricsAppService;
    private final RankingAppService rankingAppService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order-events",
            groupId = "order-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> messages, Acknowledgment acknowledgment) {
        List<OrderMetricEvent> events = new ArrayList<>(messages.size());
        for (ConsumerRecord<String, byte[]> record : messages) {
            try {
                String payload = objectMapper.readValue(record.value(), String.class);
                JsonNode node = objectMapper.readTree(payload);
                String eventId = record.topic() + ":" + record.partition() + ":" + record.offset();
                String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
                ZonedDateTime occurredAt = ZonedDateTime.parse(node.get("occurredAt").asText());

                if (!node.has("productIds")) {
                    continue;
                }
                List<Long> productIds = new ArrayList<>();
                node.get("productIds").forEach(n -> productIds.add(n.asLong()));
                long totalAmount = node.has("totalAmount") ? node.get("totalAmount").asLong() : 0;

                switch (eventType) {
                    case "OrderCreated" -> events.add(new OrderMetricEvent(
                            eventId, OrderMetricEvent.Type.CREATED, productIds, totalAmount, occurredAt));
                    case "OrderCanceled" -> events.add(new OrderMetricEvent(
                            eventId, OrderMetricEvent.Type.CANCELED, productIds, totalAmount, occurredAt));
                    default -> log.warn("알 수 없는 order 이벤트: eventType={}", eventType);
                }
            } catch (JsonProcessingException e) {
                log.error("order-events 메시지 파싱 실패 (skip): {}", new String(record.value()), e);
            } catch (Exception e) {
                log.error("order-events 파싱 실패 (skip): {}", new String(record.value()), e);
            }
        }

        try {
            productMetricsAppService.handleOrderEventBatch(events);
            // Redis hourly 는 DB 트랜잭션 밖에서 건별 처리 (log1p 가중치는 updateOrderRanking 내부에 반영)
            for (OrderMetricEvent e : events) {
                if (e.type() == OrderMetricEvent.Type.CREATED) {
                    rankingAppService.updateOrderRanking(e.productIds(), e.totalAmount());
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("order-events 배치 처리 실패 — Kafka 재전달 예정", ex);
        }
    }
}
