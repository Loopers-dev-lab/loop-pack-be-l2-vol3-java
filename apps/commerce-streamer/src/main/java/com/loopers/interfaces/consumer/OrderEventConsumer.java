package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.EventHandledService;
import com.loopers.application.ProductMetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final EventHandledService eventHandledService;
    private final ProductMetricsService productMetricsService;
    private final RankingService rankingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-events",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        groupId = "metrics-consumer"
    )
    public void consume(
        List<ConsumerRecord<String, Object>> records,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, Object> record : records) {
            try {
                Map<String, Object> event = parseEvent(record.value());

                String eventId = (String) event.get("eventId");
                String eventType = (String) event.get("eventType");
                LocalDateTime eventCreatedAt = parseCreatedAt(event.get("createdAt"));

                if (eventHandledService.isAlreadyHandled(eventId)) {
                    log.debug("이미 처리된 이벤트 skip: {}", eventId);
                    continue;
                }

                if ("ORDER_CREATED".equals(eventType)) {
                    Map<String, Object> payload = extractPayload(event);
                    log.info("주문 생성 이벤트 처리: orderId={}, createdAt={}", event.get("aggregateId"), eventCreatedAt);

                    // payload.items → 상품별 반복 → RankingService.addOrderScore() → ZINCRBY
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items =
                            (List<Map<String, Object>>) payload.get("items");
                    if (items != null) {
                        for (Map<String, Object> item : items) {
                            Long productId = ((Number) item.get("productId")).longValue();
                            int quantity = ((Number) item.get("quantity")).intValue();
                            rankingService.addOrderScore(productId, quantity);  // +0.7 * quantity
                        }
                    }
                }

                eventHandledService.markHandled(eventId);

            } catch (Exception e) {
                log.error("주문 이벤트 처리 실패: {}", record.value(), e);
            }
        }

        acknowledgment.acknowledge();
    }

    private LocalDateTime parseCreatedAt(Object value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            log.warn("createdAt 파싱 실패: {}", value);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEvent(Object value) {
        if (value instanceof Map) return (Map<String, Object>) value;
        if (value instanceof byte[] bytes) {
            try { return objectMapper.readValue(bytes, Map.class); }
            catch (Exception e) { throw new RuntimeException("이벤트 파싱 실패", e); }
        }
        if (value instanceof String str) {
            try { return objectMapper.readValue(str, Map.class); }
            catch (Exception e) { throw new RuntimeException("이벤트 파싱 실패", e); }
        }
        throw new RuntimeException("알 수 없는 이벤트 형식: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> event) {
        Object payload = event.get("payload");
        if (payload instanceof Map) return (Map<String, Object>) payload;
        return event;
    }
}
