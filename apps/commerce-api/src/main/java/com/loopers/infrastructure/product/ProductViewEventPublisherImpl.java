package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.ProductViewEvent;
import com.loopers.domain.product.ProductViewEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductViewEventPublisherImpl implements ProductViewEventPublisher {

    private static final String TOPIC = "product-view-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(ProductViewEvent.Viewed event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "productId", event.productId(),
                    "occurredAt", ZonedDateTime.now().toString()
            ));
            kafkaTemplate.send(TOPIC, String.valueOf(event.productId()), payload);
        } catch (Exception e) {
            log.error("product-view-events 발행 실패, skip. productId={}", event.productId(), e);
        }
    }
}
