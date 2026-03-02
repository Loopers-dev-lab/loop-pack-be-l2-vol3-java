package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;

    public record OrderItemRequest(Long productId, Integer quantity) {}

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        List<OrderService.OrderItemRequest> serviceRequests = itemRequests.stream()
                .map(r -> new OrderService.OrderItemRequest(r.productId(), r.quantity()))
                .toList();

        Order order = orderService.createOrder(userId, serviceRequests);
        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        order.validateOwner(userId);
        return OrderInfo.from(order);
    }

    public OrderInfo getOrderForAdmin(Long orderId) {
        Order order = orderService.getById(orderId);
        return OrderInfo.from(order);
    }

    public Page<OrderInfo> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderService.getOrdersByUserId(userId, pageable)
                .map(OrderInfo::from);
    }

    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderService.getAllOrders(pageable)
                .map(OrderInfo::from);
    }
}
