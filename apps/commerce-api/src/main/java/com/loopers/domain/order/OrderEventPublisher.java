package com.loopers.domain.order;

public interface OrderEventPublisher {

    void publish(OrderEvent.Created event);
}
