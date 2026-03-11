package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.OrderStatusHistory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * Order DB 엔티티
 */
@Entity
@Table(name = "orders")
@NoArgsConstructor
public class OrderEntity extends BaseEntity {

    @Comment("유저 id (ref)")
    @Column(name = "ref_user_id", nullable = false, updatable = false)
    private Long refUserId;

    @Comment("쿠폰 id (ref)")
    @Column(name = "ref_coupon_id")
    private Long refCouponId;

    @Comment("주문 상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Comment("쿠폰 적용 전 금액")
    @Column(name = "original_price", nullable = false)
    private Integer originalPrice;

    @Comment("할인 금액")
    @Column(name = "discount_amount", nullable = false)
    private Integer discountAmount;

    @Comment("최종 결제 금액")
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Comment("주문 일시")
    @Column(name = "order_dt", nullable = false, updatable = false)
    private ZonedDateTime orderDt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ref_order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<OrderItemEntity> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ref_order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<OrderStatusHistoryEntity> histories = new ArrayList<>();

    private OrderEntity(Long refUserId, Long refCouponId, OrderStatus status,
                        Integer originalPrice, Integer discountAmount, Integer totalPrice, ZonedDateTime orderDt) {
        this.refUserId = refUserId;
        this.refCouponId = refCouponId;
        this.status = status;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.orderDt = orderDt;
    }

    public static OrderEntity create(Order order) {
        OrderEntity entity = new OrderEntity(
            order.getRefUserId(),
            order.getRefCouponId(),
            order.getStatus(),
            order.getOriginalPrice().value(),
            order.getDiscountAmount().value(),
            order.getTotalPrice().value(),
            order.getOrderDt()
        );

        order.getItems().forEach(item ->
            entity.items.add(OrderItemEntity.create(item))
        );

        order.getHistories().forEach(history ->
            entity.histories.add(OrderStatusHistoryEntity.create(history))
        );

        return entity;
    }

    public Order toDomain() {
        List<OrderItem> domainItems = items.stream()
            .map(OrderItemEntity::toDomain)
            .toList();

        List<OrderStatusHistory> domainHistories = histories.stream()
            .map(OrderStatusHistoryEntity::toDomain)
            .toList();

        return Order.restore(
            this.getId(),
            this.refUserId,
            this.refCouponId,
            this.status,
            this.originalPrice,
            this.discountAmount,
            this.totalPrice,
            this.orderDt,
            domainItems,
            domainHistories
        );
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getRefUserId() {
        return refUserId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public ZonedDateTime getOrderDt() {
        return orderDt;
    }
}
