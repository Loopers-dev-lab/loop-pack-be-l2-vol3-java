package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.List;

public record Order(
        Long id,
        Long userId,
        String orderNumber,
        ZonedDateTime orderDate,
        OrderStatus status,
        int totalAmount,
        List<OrderItem> items,
        ZonedDateTime deletedAt
) {

    public Order {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
    }

    public Order(Long userId, String orderNumber, List<OrderItem> items) {
        this(
                null,
                userId,
                orderNumber,
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                items.stream().mapToInt(OrderItem::totalPrice).sum(),
                items,
                null
        );
    }

    public Order cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 취소된 주문입니다.");
        }
        return new Order(id, userId, orderNumber, orderDate, OrderStatus.CANCELLED, totalAmount, items, deletedAt);
    }

    public boolean isOwner(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }
}
