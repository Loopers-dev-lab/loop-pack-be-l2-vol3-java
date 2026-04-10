package com.loopers.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaEventMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.product.event.ProductLikedEvent;
import com.loopers.domain.product.event.ProductUnlikedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        publish("PRODUCT_LIKED",
                String.valueOf(event.productId()),
                Map.of("productId", event.productId(), "userId", event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        publish("PRODUCT_UNLIKED",
                String.valueOf(event.productId()),
                Map.of("productId", event.productId(), "userId", event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductViewed(ProductViewedEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", event.productId());
        data.put("userId", event.userId());

        publish("PRODUCT_VIEWED", String.valueOf(event.productId()), data);
    }

    private void publish(String eventType, String partitionKey, Map<String, Object> data) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.CATALOG_EVENTS,
                    partitionKey,
                    KafkaEventMessage.of(eventType, data));
        } catch (Exception e) {
            log.warn("[Kafka] 직접 발행 실패 (유실 허용) — eventType={}", eventType, e);
        }
    }
}
