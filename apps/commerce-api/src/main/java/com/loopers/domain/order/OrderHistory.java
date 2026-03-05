package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderHistory extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private OrderStatus newStatus;

    @Column(name = "description")
    private String description;

    private OrderHistory(Long orderId, OrderStatus previousStatus, OrderStatus newStatus, String description) {
        this.orderId = orderId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.description = description;
    }

    public static OrderHistory create(Long orderId, OrderStatus previousStatus, OrderStatus newStatus, String description) {
        return new OrderHistory(orderId, previousStatus, newStatus, description);
    }
}
