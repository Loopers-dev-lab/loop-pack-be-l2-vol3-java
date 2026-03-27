package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.infrastructure.outbox.OutboxMarkRepository;
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
    private final OutboxMarkRepository outboxMarkRepository;

    @KafkaListener(topics = "order-events", groupId = "metrics-aggregation",
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

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        String eventId = node.path("eventId").asText();
        String eventType = node.path("eventType").asText();
        JsonNode payload = objectMapper.readTree(node.path("payload").asText());

        String idempotencyKey = "metrics-aggregation:" + eventId;

        switch (eventType) {
            case "payment.completed" -> {
                Long productId = payload.path("orderId").asLong();
                BigDecimal amount = new BigDecimal(payload.path("amount").asText());
                idempotentProcessor.process(idempotencyKey, eventType,
                        () -> metricsService.incrementSales(productId, 1, amount));
            }
            case "payment.canceled" -> {
                Long productId = payload.path("orderId").asLong();
                idempotentProcessor.process(idempotencyKey, eventType,
                        () -> metricsService.incrementSales(productId, -1, BigDecimal.ZERO));
            }
            case "payment.failed" -> log.info("결제 실패 이벤트 수신: eventId={}", eventId);
            default -> log.warn("미지원 order 이벤트: eventType={}", eventType);
        }

        outboxMarkRepository.markPublished(eventId);
    }
}
