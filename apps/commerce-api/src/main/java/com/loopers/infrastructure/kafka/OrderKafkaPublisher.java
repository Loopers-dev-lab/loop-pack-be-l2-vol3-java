package com.loopers.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaEventMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.order.event.OrderCompletedEvent;
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
public class OrderKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.ORDER_EVENTS,
                    String.valueOf(event.orderId()),
                    KafkaEventMessage.of("ORDER_COMPLETED", Map.of(
                            "orderId", event.orderId(),
                            "userId", event.userId(),
                            "totalPrice", event.totalPrice())));
        } catch (Exception e) {
            log.warn("[Kafka] 직접 발행 실패 (유실 허용) — eventType=ORDER_COMPLETED", e);
        }
    }
}
