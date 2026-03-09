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

    @Column(name = "original_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<OrderItem> orderItems = new ArrayList<>();

    private Order(Long userId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal totalAmount, Long userCouponId) {
        this.userId = userId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.userCouponId = userCouponId;
        this.status = OrderStatus.CREATED;
    }

    public static Order create(Long userId, List<OrderItem> orderItems, BigDecimal discountAmount, Long userCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 없습니다.");
        }
        validateNoDuplicateProducts(orderItems);

        BigDecimal originalAmount = calculateOriginalAmount(orderItems);
        BigDecimal totalAmount = originalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        Order order = new Order(userId, originalAmount, discountAmount, totalAmount, userCouponId);
        orderItems.forEach(order::addOrderItem);
        return order;
    }

    private static void validateNoDuplicateProducts(List<OrderItem> orderItems) {
        long distinctCount = orderItems.stream().map(OrderItem::getProductId).distinct().count();
        if (distinctCount < orderItems.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
        }
    }

    private static BigDecimal calculateOriginalAmount(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
