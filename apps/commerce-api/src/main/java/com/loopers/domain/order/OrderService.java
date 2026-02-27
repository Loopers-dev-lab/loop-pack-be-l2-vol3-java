package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Order order) {
        Order savedOrder = orderRepository.save(order);
        for (OrderItem item : order.getOrderItems()) {
            item.assignOrderId(savedOrder.getId());
            orderRepository.saveItem(item);
        }
        return savedOrder;
    }
}
