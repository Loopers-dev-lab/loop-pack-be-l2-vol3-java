package com.loopers.application.order;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.event.OrderCompletedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.outbox.OutboxEventService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;

    // Command

    @Transactional
    public Order createOrder(OrderCommand.Create command) {
        Order order = Order.create(command.userId());

        command.items().forEach(item ->
                order.addItem(
                        item.productId(),
                        item.productName(),
                        item.price(),
                        item.quantity()
                )
        );

        if (command.coupon().isApplied()) {
            order.applyCoupon(
                    command.coupon().issuedCouponId(),
                    command.coupon().discountAmount()
            );
        }

        return orderRepository.save(order);
    }

    @Transactional
    public void payOrder(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다"));
        order.pay();

        List<OrderItemSnapshot> items = order.toItemSnapshots();
        OrderCompletedEvent event = new OrderCompletedEvent(
                orderId, order.getUserId(), order.getFinalAmount(), items, Instant.now());
        outboxEventService.saveAndPublish("order.completed", "Order",
                String.valueOf(orderId), KafkaTopics.ORDER_EVENTS, event);
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다"));
        order.cancel();
    }

    @Transactional
    public boolean expireIfCreated(Long orderId) {
        return orderRepository.updateStatusIfCurrent(orderId, OrderStatus.CANCELED, OrderStatus.CREATED) > 0;
    }

    // Query

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다"));
    }

    @Transactional(readOnly = true)
    public Page<Order> findAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrdersByUserIdAndStatusAndDateRange(Long userId, OrderStatus status, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return orderRepository.findAllByUserIdAndStatusAndCreatedAtBetween(userId, status, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findAllByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrdersByProductId(Long productId, Pageable pageable) {
        return orderRepository.findAllByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> findOrdersByStatusWithItems(OrderStatus status) {
        return orderRepository.findAllByStatusWithItems(status);
    }

    @Transactional(readOnly = true)
    public List<Order> findCreatedOlderThanWithItems(ZonedDateTime threshold) {
        return orderRepository.findAllByStatusAndCreatedAtBeforeWithItems(OrderStatus.CREATED, threshold);
    }

    @Transactional(readOnly = true)
    public int getCumulativePurchaseQuantity(Long userId, Long productId) {
        return orderRepository.sumQuantityByUserIdAndProductId(userId, productId);
    }
}
