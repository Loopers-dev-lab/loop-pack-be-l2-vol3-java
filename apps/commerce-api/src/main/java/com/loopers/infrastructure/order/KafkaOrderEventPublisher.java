package com.loopers.infrastructure.order;

import com.loopers.application.order.OrderEventPublisher;
import com.loopers.application.order.OrderOutboxPayload;
import com.loopers.domain.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void publish(OrderCreatedEvent event) {
        OrderOutboxPayload payload = new OrderOutboxPayload(
                event.eventId(), "OrderCreated", 1,
                event.orderId(), event.memberId(), event.totalAmount(), event.createdAt());
        kafkaTemplate.send(TOPIC, event.orderId(), payload);
    }
}
