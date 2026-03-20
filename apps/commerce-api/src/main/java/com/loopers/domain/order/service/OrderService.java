package com.loopers.domain.order.service;

import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    public Orders createOrder(OrderCommand.Create command) {
        Orders orders = Orders.create(command.memberId(), command.orderProducts(), command.discountAmount(), command.userCouponId());
        return orderRepository.save(orders);
    }

    public List<Orders> getOrders(OrderCommand.GetByPeriod command) {
        return orderRepository.findByMemberIdAndCreatedAtBetween(command.memberId(), command.startAt(), command.endAt());
    }

    public void updateOrderStatus(Long orderId, OrderStatus status) {
        orderRepository.updateStatus(orderId, status);
    }

    public Orders getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
    }

    public Orders getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
    }

    public Orders getOrder(OrderCommand.GetByMember command) {
        Orders orders = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
        if (!orders.getMemberId().equals(command.memberId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        return orders;
    }
}
