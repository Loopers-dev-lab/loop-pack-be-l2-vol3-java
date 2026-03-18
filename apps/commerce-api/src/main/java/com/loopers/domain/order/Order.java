package com.loopers.domain.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String orderKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @AttributeOverride(name = "amount", column = @Column(name = "original_total_price", nullable = false))
    private Money originalTotalPrice;

    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @AttributeOverride(name = "amount", column = @Column(name = "total_price", nullable = false))
    private Money totalPrice;

    @Column(name = "owned_coupon_id")
    private Long ownedCouponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(String orderKey, Cart cart, Money discountAmount, Long ownedCouponId) {
        List<OrderItem> orderItems = cart.cartItems().stream()
                .map(OrderItem::create)
                .toList();

        if (orderItems.isEmpty()) {
            throw new CoreException(ErrorType.REQUIRED_ORDER_ITEM);
        }
        validateNoDuplicateProducts(orderItems);

        Order order = new Order();
        order.userId = cart.userId();
        order.orderKey = orderKey;
        order.orderedAt = LocalDateTime.now();
        order.status = OrderStatus.CREATED;
        orderItems.forEach(order::addItem);
        order.name = generateOrderName(order.orderItems);
        order.originalTotalPrice = Money.sum(order.orderItems, OrderItem::calculateSubtotal);
        order.discountAmount = Money.min(discountAmount, order.originalTotalPrice);
        order.totalPrice = order.originalTotalPrice.minus(order.discountAmount);
        order.ownedCouponId = ownedCouponId;
        return order;
    }

    private static void validateNoDuplicateProducts(List<OrderItem> orderItems) {
        Set<Long> uniqueProductIds = orderItems.stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toSet());
        if (uniqueProductIds.size() != orderItems.size()) {
            throw new CoreException(ErrorType.DUPLICATE_ORDER_PRODUCT);
        }
    }

    private void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void validatePayable() {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.ORDER_NOT_PAYABLE);
        }
    }

    public void pay() {
        validatePayable();
        this.status = OrderStatus.PAID;
    }

    public void fail() {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.ORDER_NOT_CANCELLABLE);
        }
        this.status = OrderStatus.FAILED;
    }

    public boolean hasAppliedCoupon() {
        return ownedCouponId != null;
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN_ORDER_ACCESS);
        }
    }

    private static String generateOrderName(List<OrderItem> orderItems) {
        String firstName = orderItems.get(0).getProductName();
        if (orderItems.size() == 1) {
            return firstName;
        }
        return firstName + " 외 " + (orderItems.size() - 1) + "건";
    }
}
