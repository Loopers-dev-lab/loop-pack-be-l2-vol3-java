package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record Order(
        UUID id,
        String memberId,
        String orderNumber,
        ZonedDateTime orderDate,
        OrderStatus status,
        int totalAmount,
        UUID couponId,
        int usedPointAmount,
        List<OrderItem> items,
        ZonedDateTime deletedAt,
        ZonedDateTime stockDeductedAt
) {

    public Order {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
    }

    public Order(String memberId, String orderNumber, List<OrderItem> items) {
        this(
                null,
                memberId,
                orderNumber,
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                items.stream().mapToInt(OrderItem::totalPrice).sum(),
                null,
                0,
                items,
                null,
                null
        );
    }

    public Order(String memberId, String orderNumber, List<OrderItem> items, UUID couponId) {
        this(memberId, orderNumber, items, couponId, items.stream().mapToInt(OrderItem::totalPrice).sum(), 0);
    }

    public Order(String memberId, String orderNumber, List<OrderItem> items, UUID couponId, int totalAmount, int usedPointAmount) {
        this(
                null,
                memberId,
                orderNumber,
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                totalAmount,
                couponId,
                usedPointAmount,
                items,
                null,
                null
        );
    }

    public Order(UUID id, String memberId, String orderNumber, ZonedDateTime orderDate, OrderStatus status,
                 int totalAmount, List<OrderItem> items, ZonedDateTime deletedAt) {
        this(id, memberId, orderNumber, orderDate, status, totalAmount, null, 0, items, deletedAt, null);
    }

    public Order cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 취소된 주문입니다.");
        }
        return new Order(
                id,
                memberId,
                orderNumber,
                orderDate,
                OrderStatus.CANCELLED,
                totalAmount,
                couponId,
                usedPointAmount,
                items,
                ZonedDateTime.now(),
                stockDeductedAt
        );
    }

    public Order markStockDeducted() {
        if (stockDeductedAt != null) {
            return this;
        }
        if (status != OrderStatus.ORDERED) {
            return this;
        }

        return new Order(
                id,
                memberId,
                orderNumber,
                orderDate,
                status,
                totalAmount,
                couponId,
                usedPointAmount,
                items,
                deletedAt,
                ZonedDateTime.now()
        );
    }

    public boolean isOwner(String memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }

    public boolean isStockDeducted() {
        return this.stockDeductedAt != null;
    }
}
