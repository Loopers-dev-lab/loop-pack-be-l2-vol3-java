package com.loopers.infrastructure.persistence.jpa.order;

import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 JPA 엔티티.
 * Infrastructure Layer에 위치하며 영속성을 담당.
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id")
    }
)
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "original_amount")
    private Long originalAmount;

    @Column(name = "coupon_discount")
    private Long couponDiscount;

    @Column(name = "point_discount")
    private Long pointDiscount;

    @Column(name = "coupon_id")
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected OrderJpaEntity() {}

    public OrderJpaEntity(Long userId, Long totalPrice, OrderStatus status) {
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.originalAmount = totalPrice;
        this.couponDiscount = 0L;
        this.pointDiscount = 0L;
        this.couponId = null;
        this.status = status;
    }

    public OrderJpaEntity(Long userId, Long totalPrice, Long originalAmount,
            Long couponDiscount, Long pointDiscount, Long couponId, OrderStatus status) {
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.originalAmount = originalAmount;
        this.couponDiscount = couponDiscount;
        this.pointDiscount = pointDiscount;
        this.couponId = couponId;
        this.status = status;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

    public void addItem(OrderItemJpaEntity item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public List<OrderItemJpaEntity> getItems() {
        return items;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getCouponDiscount() {
        return couponDiscount;
    }

    public Long getPointDiscount() {
        return pointDiscount;
    }

    public Long getCouponId() {
        return couponId;
    }
}
