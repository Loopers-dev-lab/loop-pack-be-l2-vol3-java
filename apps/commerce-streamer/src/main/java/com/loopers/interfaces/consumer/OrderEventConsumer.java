package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public void consume(List<ConsumerRecord<String, String>> messages, Acknowledgment acknowledgment) {
        boolean hasFailure = false;
        for (ConsumerRecord<String, String> record : messages) {
            try {
                JsonNode node = objectMapper.readTree(record.value());
                String eventId = record.topic() + ":" + record.partition() + ":" + record.offset();

                String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
                ZonedDateTime occurredAt = ZonedDateTime.parse(node.get("occurredAt").asText());
                if (node.has("productIds")) {
                    List<Long> productIds = new ArrayList<>();
                    node.get("productIds").forEach(n -> productIds.add(n.asLong()));

                    long totalAmount = node.has("totalAmount") ? node.get("totalAmount").asLong() : 0;

                    switch (eventType) {
                        case "OrderCreated" -> {
                            productMetricsAppService.handleOrderCreated(eventId, productIds, totalAmount, occurredAt);
                            rankingAppService.updateOrderRanking(productIds, totalAmount);
                        }
                        case "OrderCanceled" -> productMetricsAppService.handleOrderCanceled(eventId, productIds, occurredAt);
                        default -> log.warn("알 수 없는 order 이벤트: eventType={}", eventType);
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("order-events 메시지 파싱 실패 (skip): {}", record.value(), e);
            } catch (Exception e) {
                log.error("order-events 처리 실패: {}", record.value(), e);
                hasFailure = true;
            }
        }
        if (!hasFailure) {
            acknowledgment.acknowledge();
        }
    }
}
