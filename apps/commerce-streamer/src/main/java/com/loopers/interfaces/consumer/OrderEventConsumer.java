package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsApplicationService;
import com.loopers.config.kafka.KafkaConfig;
import com.loopers.domain.event.OrderItemPayload;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private static final String DLQ_TOPIC = "order-events.dlq";

    private final MetricsApplicationService metricsApplicationService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @KafkaListener(
        topics = "order-events",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                JsonNode envelope = parseEnvelope(record.value());
                String eventId = envelope.get("eventId").asText();
                String eventType = envelope.get("eventType").asText();

                if (eventHandledRepository.existsById(eventId)) {
                    log.debug("[OrderEvent] 이미 처리된 이벤트 skip: eventId={}", eventId);
                    continue;
                }

                JsonNode data = envelope.get("data");
                processEvent(eventId, eventType, data);
                log.info("[OrderEvent] 처리 완료: eventId={}, eventType={}", eventId, eventType);
            } catch (Exception e) {
                log.error("[OrderEvent] 처리 실패 → DLQ 전송: offset={}, error={}",
                    record.offset(), e.getMessage(), e);
                sendToDlq(record);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processEvent(String eventId, String eventType, JsonNode data) throws JsonProcessingException {
        switch (eventType) {
            case "PAYMENT_COMPLETED" -> {
                List<OrderItemPayload> items = parseItems(data);
                metricsApplicationService.incrementSaleCount(eventId, items);
            }
            case "ORDER_CANCELLED" ->
                log.info("[OrderEvent] 주문 취소 이벤트 수신: orderId={}", data.get("orderId").asLong());
            case "ORDER_CREATED" ->
                log.info("[OrderEvent] 주문 생성 이벤트 수신: orderId={}", data.get("orderId").asLong());
            case "PAYMENT_FAILED" ->
                log.info("[OrderEvent] 결제 실패 이벤트 수신: orderId={}", data.get("orderId").asLong());
            default -> log.warn("[OrderEvent] 알 수 없는 이벤트 타입: {}", eventType);
        }
    }

    private List<OrderItemPayload> parseItems(JsonNode data) throws JsonProcessingException {
        return objectMapper.readValue(
            data.get("items").toString(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, OrderItemPayload.class)
        );
    }

    private void sendToDlq(ConsumerRecord<String, byte[]> record) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, record.key(), record.value());
        } catch (Exception e) {
            log.error("[OrderEvent] DLQ 전송 실패: offset={}, error={}", record.offset(), e.getMessage());
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
