package com.loopers.infrastructure.order.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.Orders;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

@Getter
@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class OrderEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int totalPrice;

    @Column(nullable = false)
    private int discountAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    protected OrderEntity() {}

    private OrderEntity(String orderNumber, Long memberId, int totalPrice, int discountAmount, Long userCouponId, OrderStatus status) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.discountAmount = discountAmount;
        this.userCouponId = userCouponId;
        this.status = status;
    }

    public static OrderEntity toEntity(Orders orders) {
        return new OrderEntity(
                orders.getOrderNumber(),
                orders.getMemberId(),
                orders.getTotalPrice().value(),
                orders.getDiscountAmount().value(),
                orders.getUserCouponId(),
                orders.getStatus()
        );
    }

    public Orders toModel() {
        return Orders.reconstruct(this.getId(), this.orderNumber, this.memberId, this.totalPrice, this.discountAmount, this.userCouponId, this.status, List.of());
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }
}
