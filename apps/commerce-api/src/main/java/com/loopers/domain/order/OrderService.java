package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order create(Long userId, String orderNumber, List<OrderItem> items,
                        String ordererName, String ordererPhone,
                        String receiverName, String receiverPhone,
                        String zipCode, String addressLine1, String addressLine2) {
        Order order = Order.create(userId, orderNumber, items,
                ordererName, ordererPhone, receiverName, receiverPhone,
                zipCode, addressLine1, addressLine2);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(OrderErrorType.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        order.validateOwnership(userId);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByUserId(userId, startAt, endAt);
    }

    @Transactional
    public Order cancel(Long orderId, Long userId) {
        Order order = getById(orderId);
        order.validateOwnership(userId);
        order.cancel();
        return order;
    }

    @Transactional
    public void confirm(Long orderId, Long paymentId, String paymentMethod) {
        Order order = getById(orderId);
        order.confirm(paymentId, paymentMethod);
    }
}
