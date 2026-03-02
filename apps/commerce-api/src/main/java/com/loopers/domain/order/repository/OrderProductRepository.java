package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.OrderProduct;

import java.util.List;

public interface OrderProductRepository {

    List<OrderProduct> saveAll(Long orderId, List<OrderProduct> orderProducts);

    List<OrderProduct> findByOrderId(Long orderId);
}
