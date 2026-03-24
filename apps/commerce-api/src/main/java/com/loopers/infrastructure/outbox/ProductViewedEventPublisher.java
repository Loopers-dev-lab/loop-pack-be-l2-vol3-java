package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 조회 이벤트는 유실 허용 가능하므로 Outbox를 거치지 않고 Kafka에 직접 발행한다.
 * 고빈도 조회마다 REQUIRES_NEW TX + Outbox INSERT를 방지.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ProductViewedEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "eventType", "PRODUCT_VIEWED",
                "data", event
            );
            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("catalog-events", String.valueOf(event.productId()), payload);
        } catch (JsonProcessingException e) {
            log.warn("[ProductViewed] 직렬화 실패: productId={}, error={}",
                event.productId(), e.getMessage());
        } catch (Exception e) {
            log.warn("[ProductViewed] Kafka 발행 실패: productId={}, error={}",
                event.productId(), e.getMessage());
        }
    }
}
