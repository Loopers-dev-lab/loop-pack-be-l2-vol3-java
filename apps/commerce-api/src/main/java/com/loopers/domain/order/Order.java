package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.exception.InvalidOrderStatusTransitionException;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_user_id", columnList = "user_id"),
        @Index(name = "idx_order_created_at", columnList = "created_at"),
        @Index(name = "idx_order_user_created_at", columnList = "user_id, created_at")
    }
)
@Getter
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> orderItems = new ArrayList<>();

    protected Order() {
    }

    private Order(Long userId) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
    }

    public static Order create(Long userId, List<OrderItem> orderItems) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 1개 이상이어야 합니다");
        }

        Order order = new Order(userId);
        for (OrderItem orderItem : orderItems) {
            order.addOrderItem(orderItem);
        }
        return order;
    }

    public void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("주문 항목은 필수입니다");
        }

        orderItem.assignOrder(this);
        this.orderItems.add(orderItem);
        this.totalAmount = this.totalAmount.add(orderItem.getSubtotal());
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    public void changeStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("주문 상태는 필수입니다");
        }
        if (!canTransitionTo(status)) {
            throw new InvalidOrderStatusTransitionException(this.status, status);
        }
        this.status = status;
    }

    public boolean cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return false;
        }
        this.changeStatus(OrderStatus.CANCELLED);
        return true;
    }

    @Override
    protected void guard() {
        if (this.userId == null || this.userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (this.status == null) {
            throw new IllegalArgumentException("주문 상태는 필수입니다");
        }
        if (this.totalAmount == null || this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다");
        }
    }

    private boolean canTransitionTo(OrderStatus to) {
        if (this.status == to) {
            return true;
        }
        if (this.status == OrderStatus.PENDING) {
            return to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
        }
        if (this.status == OrderStatus.CONFIRMED) {
            return to == OrderStatus.CANCELLED;
        }
        return false;
    }
}
