package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Order {
    private final Long id;
    private final Long userId;
    private final List<OrderItem> orderItems;
    private OrderStatus status;

    private Order(Long id, Long userId, List<OrderItem> orderItems, OrderStatus status) {
        validateUserId(userId);
        validateOrderItems(orderItems);
        this.id = id;
        this.userId = userId;
        this.orderItems = new ArrayList<>(orderItems);
        this.status = status;
    }

    public static Order create(Long userId, List<OrderItem> orderItems) {
        return new Order(null, userId, orderItems, OrderStatus.PENDING);
    }

    public static Order of(Long id, Long userId, List<OrderItem> orderItems, OrderStatus status) {
        return new Order(id, userId, orderItems, status);
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    public Money getTotalAmount() {
        return orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money.zero(), Money::add);
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태에서만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void prepare() {
        if (this.status != OrderStatus.PAID) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 완료 상태에서만 준비할 수 있습니다.");
        }
        this.status = OrderStatus.PREPARING;
    }

    public void ship() {
        if (!this.status.canShip()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "준비 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void deliver() {
        if (!this.status.canDeliver()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "배송 중 상태에서만 배송 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void cancel() {
        if (!this.status.canCancel()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.CANCELED;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateOrderItems(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }
    }
}
