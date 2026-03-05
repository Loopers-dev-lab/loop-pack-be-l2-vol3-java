package com.loopers.domain.order;

import java.util.List;

public interface OrderHistoryRepository {
    OrderHistory save(OrderHistory orderHistory);
    List<OrderHistory> findAllByOrderId(Long orderId);
}
