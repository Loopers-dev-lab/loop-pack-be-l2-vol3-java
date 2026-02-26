package com.loopers.application.order;

import com.loopers.domain.model.user.UserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderQueryUseCase {

    List<OrderSummary> getMyOrders(UserId userId);

    List<OrderSummary> getMyOrders(UserId userId, LocalDate startAt, LocalDate endAt);

    List<OrderSummary> getAllOrders();

    OrderDetail getOrderDetail(Long orderId);

    OrderDetail getOrder(UserId userId, Long orderId);

    record OrderSummary(
            Long id,
            String status,
            int paymentAmount,
            LocalDateTime createdAt
    ) {}

    record OrderDetail(
            Long id,
            String receiverName,
            String address,
            String deliveryRequest,
            String paymentMethod,
            int totalAmount,
            int discountAmount,
            int paymentAmount,
            String status,
            List<OrderItemDetail> items,
            LocalDateTime createdAt
    ) {}

    record OrderItemDetail(
            Long productId,
            int quantity,
            int unitPrice
    ) {}
}
