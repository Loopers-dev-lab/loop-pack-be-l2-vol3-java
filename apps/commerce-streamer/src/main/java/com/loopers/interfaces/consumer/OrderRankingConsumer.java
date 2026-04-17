package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ConsumerMetrics;
import com.loopers.application.metrics.OrderMetricProcessor;
import com.loopers.application.metrics.OrderMetricProcessor.OrderItemMetric;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRankingConsumer {

    private static final String GROUP_ID = "ranking-consumer";
    private static final String TOPIC = KafkaTopics.ORDER_EVENTS;

    private final OrderMetricProcessor orderMetricProcessor;
    private final ObjectMapper objectMapper;
    private final ConsumerMetrics consumerMetrics;

    @KafkaListener(
            id = "orderRankingConsumer",
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
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

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode envelope = objectMapper.readTree(record.value());
        String eventId = envelope.path("eventId").asText();
        String eventType = envelope.path("eventType").asText();

        if (!"order.completed".equals(eventType)) {
            return;
        }

        JsonNode payload = objectMapper.readTree(envelope.path("payload").asText());
        Instant occurredAt = Instant.parse(payload.path("occurredAt").asText());

        JsonNode items = payload.path("items");
        List<OrderItemMetric> orderItems = new ArrayList<>();
        for (JsonNode item : items) {
            Long productId = item.path("productId").asLong();
            int quantity = item.path("quantity").asInt();
            long unitPrice = item.path("unitPrice").asLong();
            orderItems.add(new OrderItemMetric(productId, quantity, unitPrice * quantity));
        }

        orderMetricProcessor.process(eventId, eventType, TOPIC, GROUP_ID, occurredAt, orderItems);
    }
}
