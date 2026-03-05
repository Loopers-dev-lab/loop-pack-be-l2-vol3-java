package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    private Order(Long userId, List<OrderItem> orderItems, OrderStatus status, Long issuedCouponId, Money discountAmount) {
        validateUserId(userId);
        validateOrderItems(orderItems);
        this.userId = userId;
        this.orderItems = new ArrayList<>(orderItems);
        this.status = status;
        this.issuedCouponId = issuedCouponId;
        this.discountAmount = discountAmount != null ? discountAmount : Money.zero();
    }

    public static Order create(Long userId, List<OrderItem> orderItems) {
        Order order = new Order(userId, orderItems, OrderStatus.PENDING, null, Money.zero());
        orderItems.forEach(item -> item.setOrder(order));
        return order;
    }

    public static Order create(Long userId, List<OrderItem> orderItems, Long issuedCouponId, Money discountAmount) {
        Order order = new Order(userId, orderItems, OrderStatus.PENDING, issuedCouponId, discountAmount);
        orderItems.forEach(item -> item.setOrder(order));
        return order;
    }

    public Money getPaymentAmount() {
        return getTotalAmount().subtract(discountAmount);
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    public Money getTotalAmount() {
        return orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money.zero(), Money::add);
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태에서만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void prepare() {
        if (this.status != OrderStatus.PAID) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 완료 상태에서만 준비할 수 있습니다.");
        }
        this.status = OrderStatus.PREPARING;
    }

    public void ship() {
        if (!this.status.canShip()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "준비 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void deliver() {
        if (!this.status.canDeliver()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "배송 중 상태에서만 배송 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void cancel() {
        if (!this.status.canCancel()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.CANCELED;
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회/취소할 수 있습니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateOrderItems(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }
    }
}
