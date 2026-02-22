package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemJpaEntity> orderItems = new ArrayList<>();

    private OrderJpaEntity(Long userId, OrderStatus status) {
        this.userId = userId;
        this.status = status;
    }

    public static OrderJpaEntity from(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(order.getUserId(), order.getStatus());
        for (OrderItem item : order.getOrderItems()) {
            entity.addOrderItem(OrderItemJpaEntity.of(entity, item));
        }
        return entity;
    }

    public void addOrderItem(OrderItemJpaEntity orderItem) {
        this.orderItems.add(orderItem);
    }

    public Order toDomain() {
        List<OrderItem> domainItems = orderItems.stream()
                .map(OrderItemJpaEntity::toDomain)
                .toList();
        return Order.of(getId(), userId, domainItems, status);
    }

    public void update(Order order) {
        this.status = order.getStatus();
    }
}
