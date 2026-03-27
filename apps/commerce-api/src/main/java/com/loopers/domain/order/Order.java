package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_status_created", columnList = "user_id, status, created_at DESC"),
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_orders_status_created", columnList = "status, created_at DESC")
})
@Getter
public class Order {

    private static final int ORDER_ITEMS_MAX_SIZE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id = 0L;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected Order() {
    }

    public static Order create(Long userId) {
        validateUserId(userId);
        Order order = new Order();
        order.userId = userId;
        order.status = OrderStatus.CREATED;
        order.totalAmount = BigDecimal.ZERO;
        order.discountAmount = BigDecimal.ZERO;
        order.finalAmount = BigDecimal.ZERO;
        return order;
    }

    public void addItem(Long productId, String productName,
                        BigDecimal price, int quantity) {
        validateMaxSize();
        validateDuplicateProduct(productId);

        OrderItem item = OrderItem.create(productId, productName, price, quantity);
        item.assignOrder(this);
        this.orderItems.add(item);
        this.totalAmount = this.totalAmount.add(item.getOrderPrice());
        this.finalAmount = this.totalAmount;
    }

    public void applyCoupon(Long issuedCouponId, BigDecimal discountAmount) {
        this.issuedCouponId = issuedCouponId;
        this.discountAmount = discountAmount;
        this.finalAmount = this.totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
    }

    public void pay() {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다");
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.status != OrderStatus.PAID && this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다");
        }
        this.status = OrderStatus.CANCELED;
    }

    public Map<Long, Integer> getProductQuantities() {
        return orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
    }

    public boolean isPaid() {
        return this.status == OrderStatus.PAID;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void validateOwnership(Long userId) {
        if (!isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
    }

    private void validateMaxSize() {
        if (orderItems.size() >= ORDER_ITEMS_MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 100개 이하여야 합니다");
        }
    }

    private void validateDuplicateProduct(Long productId) {
        boolean exists = orderItems.stream()
                .anyMatch(item -> item.getProductId().equals(productId));
        if (exists) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다");
        }
    }
}
