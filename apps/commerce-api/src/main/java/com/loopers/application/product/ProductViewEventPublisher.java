package com.loopers.application.product;

import com.loopers.event.Event;
import com.loopers.event.EventType;
import com.loopers.event.Snowflake;
import com.loopers.event.Topic;
import com.loopers.event.payload.ProductViewedEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductViewEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ProductViewEventPublisher(@Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    private final Snowflake eventIdSnowflake = new Snowflake();

    public void publish(Long productId) {
        try {
            String json = Event.of(
                    eventIdSnowflake.nextId(),
                    EventType.PRODUCT_VIEWED,
                    ProductViewedEventPayload.of(productId, null)
            ).toJson();
            kafkaTemplate.send(Topic.CATALOG_EVENTS, String.valueOf(productId), json);
        } catch (Exception e) {
            log.warn("[ProductViewEventPublisher] 조회 이벤트 발행 실패, productId={}", productId, e);
        }
    }
}
