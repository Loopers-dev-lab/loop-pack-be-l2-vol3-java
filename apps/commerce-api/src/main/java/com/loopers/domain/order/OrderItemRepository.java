package com.loopers.domain.order;

import java.util.List;

public interface OrderItemRepository {

    OrderItemModel save(OrderItemModel orderItemModel);

    List<OrderItemModel> findAllByOrderId(Long orderId);
}
