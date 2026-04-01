package com.loopers.domain.order;

import com.loopers.domain.common.CursorResult;
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
        Order order = Order.place(userId, orderNumber, items,
                ordererName, ordererPhone, receiverName, receiverPhone,
                zipCode, addressLine1, addressLine2);
        return orderRepository.save(order);
    }

    @Transactional
    public Order createWithDiscount(Long userId, String orderNumber, List<OrderItem> items,
                                     String ordererName, String ordererPhone,
                                     String receiverName, String receiverPhone,
                                     String zipCode, String addressLine1, String addressLine2,
                                     int discountAmount, int pointUsedAmount, int shippingFee, Long couponId) {
        Order order = Order.place(userId, orderNumber, items,
                ordererName, ordererPhone, receiverName, receiverPhone,
                zipCode, addressLine1, addressLine2);
        order.applyDiscount(discountAmount, pointUsedAmount, shippingFee, couponId);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(OrderErrorType.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Order getByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CoreException(OrderErrorType.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        order.validateOwnership(userId);
        return order;
    }

    @Transactional(readOnly = true)
    public CursorResult<Order> getOrdersWithCursor(Long userId, ZonedDateTime startAt, ZonedDateTime endAt,
                                                    ZonedDateTime cursorCreatedAt, Long cursorId, int size) {
        return orderRepository.findAllByUserIdWithCursor(userId, startAt, endAt, cursorCreatedAt, cursorId, size);
    }

    @Transactional
    public Order cancel(Long orderId, Long userId) {
        Order order = getById(orderId);
        order.validateOwnership(userId);
        order.cancel();
        return orderRepository.save(order);
    }

    // --- 어드민 기능 ---

    @Transactional(readOnly = true)
    public List<Order> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public long countAllOrders() {
        return orderRepository.count();
    }

    @Transactional
    public void confirm(Long orderId, Long paymentId, String paymentMethod) {
        Order order = getById(orderId);
        order.confirm(paymentId, paymentMethod);
        orderRepository.save(order);
    }
}
