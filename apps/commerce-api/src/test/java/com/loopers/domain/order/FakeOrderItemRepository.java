package com.loopers.domain.order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeOrderItemRepository implements OrderItemRepository {

    private final Map<Long, OrderItemModel> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderItemModel save(OrderItemModel orderItemModel) {
        if (orderItemModel.getId() == 0L) {
            try {
                var idField = orderItemModel.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(orderItemModel, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        store.put(orderItemModel.getId(), orderItemModel);
        return orderItemModel;
    }

    @Override
    public List<OrderItemModel> findAllByOrderId(Long orderId) {
        return store.values().stream()
                .filter(item -> item.getOrder() != null
                        && item.getOrder().getId() == orderId)
                .toList();
    }
}
