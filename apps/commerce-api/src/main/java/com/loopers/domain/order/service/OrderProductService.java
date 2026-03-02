package com.loopers.domain.order.service;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.repository.OrderProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderProductService {

    private final OrderProductRepository orderProductRepository;

    public List<OrderProduct> saveAll(Long orderId, List<OrderProduct> orderProducts) {
        return orderProductRepository.saveAll(orderId, orderProducts);
    }

    public List<OrderProduct> findByOrderId(Long orderId) {
        return orderProductRepository.findByOrderId(orderId);
    }
}
