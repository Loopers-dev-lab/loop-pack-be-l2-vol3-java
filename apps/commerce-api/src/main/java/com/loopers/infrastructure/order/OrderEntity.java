package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    @Getter
    private String memberId;

    @Column(name = "order_number", nullable = false, unique = true)
    @Getter
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    @Getter
    private ZonedDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    @Getter
    private int totalAmount;

    @Column(name = "coupon_id")
    @Getter
    private UUID couponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    public OrderEntity(String memberId, String orderNumber, ZonedDateTime orderDate, OrderStatus status, int totalAmount, UUID couponId) {
        this.memberId = memberId;
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
        this.couponId = couponId;
    }

    public static OrderEntity from(Order order) {
        return new OrderEntity(
                order.memberId(),
                order.orderNumber(),
                order.orderDate(),
                order.status(),
                order.totalAmount(),
                order.couponId()
        );
    }

    public void addItem(OrderItemEntity item) {
        this.items.add(item);
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public Order toDomain() {
        return new Order(
                getId(),
                memberId,
                orderNumber,
                orderDate,
                status,
                totalAmount,
                couponId,
                items.stream().map(OrderItemEntity::toDomain).toList(),
                getDeletedAt()
        );
    }
}
