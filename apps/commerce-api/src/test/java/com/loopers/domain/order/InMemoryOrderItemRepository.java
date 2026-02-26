package com.loopers.domain.order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryOrderItemRepository implements OrderItemRepository {
    private final Map<Long, OrderItem> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == 0L) {
            try {
                var idField = orderItem.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(orderItem, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return store.values().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .toList();
    }
}
