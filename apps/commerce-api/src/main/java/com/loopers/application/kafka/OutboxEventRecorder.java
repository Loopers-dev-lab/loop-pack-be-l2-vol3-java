package com.loopers.application.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.OrderCompletedEvent;
import com.loopers.application.event.ProductLikedEvent;
import com.loopers.application.event.ProductUnlikedEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ApplicationEvent를 수신하여 Outbox 테이블에 기록하는 리스너.
 *
 * @EventListener를 사용하여 비즈니스 TX 안에서 동기 실행됨.
 * → outbox INSERT가 비즈니스 데이터와 같은 TX에서 원자적으로 저장됨.
 *
 * Kafka 전파 대상 이벤트만 기록:
 * - ProductLikedEvent → catalog-events 토픽
 * - ProductUnlikedEvent → catalog-events 토픽
 * - OrderCompletedEvent → order-events 토픽
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleProductLiked(ProductLikedEvent event) {
        outboxEventService.recordEvent(
            OutboxAggregateType.PRODUCT,
            String.valueOf(event.productId()),
            OutboxEventType.PRODUCT_LIKED,
            toJson(Map.of("productId", event.productId()))
        );
    }

    @EventListener
    public void handleProductUnliked(ProductUnlikedEvent event) {
        outboxEventService.recordEvent(
            OutboxAggregateType.PRODUCT,
            String.valueOf(event.productId()),
            OutboxEventType.PRODUCT_UNLIKED,
            toJson(Map.of("productId", event.productId()))
        );
    }

    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        outboxEventService.recordEvent(
            OutboxAggregateType.PRODUCT,
            String.valueOf(event.productId()),
            OutboxEventType.PRODUCT_VIEWED,
            toJson(Map.of("productId", event.productId()))
        );
    }

    @EventListener
    public void handleOrderCompleted(OrderCompletedEvent event) {
        outboxEventService.recordEvent(
            OutboxAggregateType.ORDER,
            String.valueOf(event.orderId()),
            OutboxEventType.ORDER_COMPLETED,
            toJson(orderPayload(event))
        );
    }

    private Map<String, Object> orderPayload(OrderCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", event.orderId());
        payload.put("memberId", event.memberId());
        payload.put("totalAmount", event.totalAmount());
        payload.put("productIds", event.productIds());
        return payload;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }
}
