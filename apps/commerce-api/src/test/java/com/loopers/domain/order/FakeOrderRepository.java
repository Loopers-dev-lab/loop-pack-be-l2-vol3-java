package com.loopers.domain.order;

import java.util.ArrayList;
import java.util.List;

public class FakeOrderRepository implements OrderRepository {

    private final List<Order> orders = new ArrayList<>();
    private final List<OrderItem> items = new ArrayList<>();
    private long orderSequence = 1L;
    private long itemSequence = 1L;

    @Override
    public Order save(Order order) {
        orders.add(order);
        return order;
    }

    @Override
    public OrderItem saveItem(OrderItem orderItem) {
        items.add(orderItem);
        return orderItem;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
