package com.loopers.application.order;

import com.loopers.domain.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderAppService orderAppService;

    public Order createOrder(OrderCreateCommand command) {
        return orderAppService.createOrder(command);
    }

    public Order createOrderFromCart(Long userId, List<Long> cartItemIds, Long couponId) {
        return orderAppService.createOrderFromCart(userId, cartItemIds, couponId);
    }

    public Order cancelOrder(Long userId, Long orderId) {
        return orderAppService.cancelOrder(userId, orderId);
    }

    public Order getOrder(Long userId, Long orderId) {
        Order order = orderAppService.getById(orderId);
        order.validateOwner(userId);
        return order;
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderAppService.getByUserId(userId);
    }

    public List<Order> getAll() {
        return orderAppService.getAll();
    }

    public Page<Order> getAll(Pageable pageable) {
        return orderAppService.getAll(pageable);
    }

    public Order getById(Long orderId) {
        return orderAppService.getById(orderId);
    }

    public Order payOrder(Long orderId) {
        return orderAppService.pay(orderId);
    }

    public Order prepareOrder(Long orderId) {
        return orderAppService.prepare(orderId);
    }

    public Order shipOrder(Long orderId) {
        return orderAppService.ship(orderId);
    }

    public Order deliverOrder(Long orderId) {
        return orderAppService.deliver(orderId);
    }
}
