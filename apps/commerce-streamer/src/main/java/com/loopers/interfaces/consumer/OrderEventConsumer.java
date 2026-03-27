package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "commerce-streamer")
    public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventId = node.path("eventId").asText();
            String eventType = node.path("eventType").asText();
            JsonNode payload = objectMapper.readTree(node.path("payload").asText());

            switch (eventType) {
                case "payment.completed" -> {
                    Long productId = payload.path("orderId").asLong();
                    BigDecimal amount = new BigDecimal(payload.path("amount").asText());
                    idempotentProcessor.process(eventId, eventType,
                            () -> metricsService.incrementSales(productId, 1, amount));
                }
                case "payment.canceled" -> {
                    Long productId = payload.path("orderId").asLong();
                    idempotentProcessor.process(eventId, eventType,
                            () -> metricsService.incrementSales(productId, -1, BigDecimal.ZERO));
                }
                case "payment.failed" -> log.info("결제 실패 이벤트 수신: eventId={}", eventId);
                default -> log.warn("미지원 order 이벤트: eventType={}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("order 이벤트 처리 실패: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
