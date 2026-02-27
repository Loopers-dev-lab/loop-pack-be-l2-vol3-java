package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Transient
    private List<OrderItem> orderItems = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, List<OrderItem> orderItems) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다");
        }
        this.userId = userId;
        this.orderItems = new ArrayList<>(orderItems);
        this.totalPrice = calculateTotalPrice();
    }

    private long calculateTotalPrice() {
        return orderItems.stream()
            .mapToLong(OrderItem::totalPrice)
            .sum();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }
}
