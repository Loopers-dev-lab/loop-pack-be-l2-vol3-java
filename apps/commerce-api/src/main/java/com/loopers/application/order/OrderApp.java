package com.loopers.application.order;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.OrderItemRequest;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderApp {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OutboxAppender outboxAppender;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items) {
        return createOrder(memberId, items, BigDecimal.ZERO, null);
    }

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items,
                                 BigDecimal discountAmount, Long refUserCouponId) {
        List<OrderItemRequest> orderItems = items.stream()
                .map(OrderItemCommand::toOrderItemRequest)
                .toList();
        OrderModel order = orderService.createOrder(memberId, orderItems, discountAmount, refUserCouponId);

        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        List<OrderItemPayload> itemPayloads = order.getOrderItems().stream()
                .map(item -> new OrderItemPayload(item.getRefProductId(), item.getPrice(), item.getQuantity()))
                .toList();
        OrderOutboxPayload payload = new OrderOutboxPayload(
                eventId, "OrderCreated", 2,
                order.getOrderId().value(), memberId, order.getFinalAmount(), now, itemPayloads);
        outboxAppender.append("order", order.getOrderId().value(), "OrderCreated", ORDER_EVENTS_TOPIC, payload);
        eventPublisher.publishEvent(new OrderCreatedEvent(eventId, order.getOrderId().value(), memberId, order.getFinalAmount(), now));

        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateOriginalAmount(List<OrderItemCommand> items) {
        List<OrderItemRequest> orderItems = items.stream()
                .map(OrderItemCommand::toOrderItemRequest)
                .toList();
        return orderService.calculateOriginalAmount(orderItems);
    }

    @Transactional
    public void markOrderPaid(Long orderId) {
        orderService.markPaid(orderId);
    }

    @Transactional
    public OrderInfo cancelOrder(Long memberId, String orderId) {
        OrderModel order = orderService.cancelOrder(memberId, orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrder(Long memberId, String orderId) {
        return OrderInfo.from(orderService.getMyOrder(memberId, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getMyOrders(Long memberId, LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable) {
        return orderRepository.findByRefMemberId(new RefMemberId(memberId), startDateTime, endDateTime, pageable)
                .map(OrderInfo::from);
    }
}
