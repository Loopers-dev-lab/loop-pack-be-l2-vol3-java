package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductViewEventHandler {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleViewed(ProductViewedEvent event) {
        try {
            log.info("상품 조회: viewerId={}, productId={}, occurredAt={}",
                    event.viewerId(), event.productId(), event.occurredAt());
            String payloadJson = objectMapper.writeValueAsString(event);
            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "product.viewed",
                    "payload", payloadJson
            );
            kafkaTemplate.send(KafkaTopics.PRODUCT_VIEW_EVENTS, String.valueOf(event.productId()), envelope)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("kafka send failed: productId = {}", event.productId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("상품 조회 이벤트 발행 실패: productId={}", event.productId(), e);
        }
    }
}
