package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "used_coupon_id")
    private Long usedCouponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> orderItems = new ArrayList<>();

    public OrderModel(Long userId, List<OrderItemModel> orderItems) {
        this(userId, orderItems, 0L, null);
    }

    public OrderModel(Long userId, List<OrderItemModel> orderItems, Long discountAmount, Long usedCouponId) {
        validate(userId, orderItems, discountAmount, usedCouponId);
        this.userId = userId;
        this.originalAmount = 0L;
        this.discountAmount = 0L;
        this.totalAmount = 0L;
        this.usedCouponId = usedCouponId;
        orderItems.forEach(this::addOrderItem);
        applyDiscount(discountAmount);
    }

    private void addOrderItem(OrderItemModel orderItem) {
        orderItem.setOrder(this);
        this.orderItems.add(orderItem);
        this.originalAmount += orderItem.getLineTotalAmount();
    }

    private void applyDiscount(Long discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount : 0L;
        this.totalAmount = this.originalAmount - this.discountAmount;
    }

    private void validate(Long userId, List<OrderItemModel> orderItems, Long discountAmount, Long usedCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 최소 1개 이상이어야 합니다.");
        }
        if (discountAmount != null && discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }

        long grossAmount = orderItems.stream()
            .mapToLong(OrderItemModel::getLineTotalAmount)
            .sum();
        if (discountAmount != null && discountAmount > grossAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.");
        }
        if (usedCouponId != null && (discountAmount == null || discountAmount == 0L)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 사용 시 할인 금액은 1 이상이어야 합니다.");
        }
        if (usedCouponId == null && discountAmount != null && discountAmount > 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 없이 할인 금액을 적용할 수 없습니다.");
        }
    }
}
