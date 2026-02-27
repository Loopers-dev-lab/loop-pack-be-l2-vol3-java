package com.loopers.domain.order;

public interface OrderRepository {

    Order save(Order order);

    OrderItem saveItem(OrderItem orderItem);
}
