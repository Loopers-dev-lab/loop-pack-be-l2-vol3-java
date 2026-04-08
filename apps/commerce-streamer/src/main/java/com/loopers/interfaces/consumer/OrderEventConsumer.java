package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "metrics-aggregation",
            containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            try {
                processRecord(records.get(i));
            } catch (Exception e) {
                throw new BatchListenerFailedException("order 이벤트 처리 실패", e, i);
            }
        }
        ack.acknowledge();
    }

    private static final String TOPIC = KafkaTopics.ORDER_EVENTS;
    private static final String GROUP_ID = "metrics-aggregation";

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        String eventId = node.path("eventId").asText();
        String eventType = node.path("eventType").asText();
        JsonNode payload = objectMapper.readTree(node.path("payload").asText());

        String idempotencyKey = GROUP_ID + ":" + eventId;

        switch (eventType) {
            case "payment.completed" -> idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                    () -> applyItems(payload, 1));
            case "payment.canceled" -> idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                    () -> applyItems(payload, -1));
            case "payment.failed" -> log.info("결제 실패 이벤트 수신: eventId={}", eventId);
            default -> log.warn("미지원 order 이벤트: eventType={}", eventType);
        }
    }

    /**
     * payload.items를 펼쳐 각 item에 대해 incrementSales를 호출한다.
     * sign=+1이면 결제 완료(증가), -1이면 결제 취소(감소).
     */
    private void applyItems(JsonNode payload, int sign) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) {
            log.warn("payment 이벤트 payload에 items 배열 없음. orderId={}", payload.path("orderId").asLong());
            return;
        }
        for (JsonNode item : items) {
            Long productId = item.path("productId").asLong();
            int quantity = item.path("quantity").asInt();
            BigDecimal unitPrice = new BigDecimal(item.path("unitPrice").asText());
            long countDelta = (long) sign * quantity;
            BigDecimal amountDelta = unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .multiply(BigDecimal.valueOf(sign));
            metricsService.incrementSales(productId, countDelta, amountDelta);
        }
    }
}
