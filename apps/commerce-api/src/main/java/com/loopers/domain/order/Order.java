package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<OrderItem> orderItems = new ArrayList<>();

    private Order(Long userId, BigDecimal totalAmount) {
        this.userId = userId;
        this.totalAmount = totalAmount;
    }

    public static Order create(Long userId, List<OrderItem> orderItems) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(userId, totalAmount);
        orderItems.forEach(order::addOrderItem);
        return order;
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 조회할 수 없습니다.");
        }
    }

    private void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
}
