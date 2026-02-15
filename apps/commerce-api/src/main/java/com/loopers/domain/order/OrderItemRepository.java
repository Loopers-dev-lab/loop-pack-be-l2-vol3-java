package com.loopers.domain.order;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository {

    OrderItem save(OrderItem orderItem);

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByOrderIds(Collection<Long> orderIds);
}
