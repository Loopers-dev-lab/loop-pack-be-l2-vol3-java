package com.loopers.application.order;

import com.loopers.domain.order.event.OrderCreatedEvent;

public interface OrderEventPublisher {
    void publish(OrderCreatedEvent event);
}
