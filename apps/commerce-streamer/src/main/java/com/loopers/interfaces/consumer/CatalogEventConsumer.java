package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.EventHandledService;
import com.loopers.application.ProductMetricsService;
import com.loopers.confg.kafka.KafkaConfig;
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
public class CatalogEventConsumer {

    private final EventHandledService eventHandledService;
    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "catalog-events",
            containerFactory = KafkaConfig.BATCH_LISTENER,
            groupId = "metrics-consumer"
    )
    public void consume(
            List<ConsumerRecord<String, Object>> records,
            Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, Object> record : records) {
            try {
                // Outbox Relay가 보낸 payload는 JSON String
                // record.value()를 파싱하여 eventType, aggregateId 등을 추출
                Map<String, Object> event = parseEvent(record.value());

                String eventId = (String) event.get("eventId");
                String eventType = (String) event.get("eventType");
                Long aggregateId = ((Number) event.get("aggregateId")).longValue();
                LocalDateTime eventCreatedAt = parseCreatedAt(event.get("createdAt"));

                // 1. 멱등 체크
                if (eventHandledService.isAlreadyHandled(eventId)) {
                    log.debug("이미 처리된 이벤트 skip: {}", eventId);
                    continue;
                }

                // 2. updatedAt 기준 최신 이벤트만 반영
                if (eventCreatedAt != null
                        && productMetricsService.isStaleEvent(aggregateId, eventCreatedAt)) {
                    log.info("이전 이벤트 skip: productId={}, eventCreatedAt={}", aggregateId, eventCreatedAt);
                    eventHandledService.markHandled(eventId);
                    continue;
                }

                // 3. 이벤트 타입별 처리
                switch (eventType) {
                    case "PRODUCT_VIEWED" -> productMetricsService.incrementViewCount(aggregateId);
                    case "PRODUCT_LIKED" -> productMetricsService.incrementLikeCount(aggregateId);
                    case "PRODUCT_UNLIKED" -> productMetricsService.decrementLikeCount(aggregateId);
                    default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
                }

                // 3. 처리 완료 기록
                eventHandledService.markHandled(eventId);

            } catch (Exception e) {
                log.error("이벤트 처리 실패: {}", record.value(), e);
            }
        }

        // 4. 배치 전체 acknowledge
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
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof String str) {
            try {
                return objectMapper.readValue(str, Map.class);
            } catch (Exception e) {
                throw new RuntimeException("이벤트 파싱 실패", e);
            }
        }
        throw new RuntimeException("알 수 없는 이벤트 형식: " + value.getClass());
    }
}
