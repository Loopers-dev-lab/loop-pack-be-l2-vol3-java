package com.loopers.application.outbox;

import com.loopers.application.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDirectEventListener {

    private static final String CATALOG_EVENTS_TOPIC = "catalog.events.topic-v1";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            Map<String, Object> message = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PRODUCT_VIEWED",
                "productId", event.productId(),
                "occurredAt", event.viewedAt().toString()
            );
            kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(event.productId()), message);
        } catch (Exception e) {
            log.warn("상품 조회 이벤트 Kafka 발행 실패 - productId: {}", event.productId(), e);
        }
    }
}
