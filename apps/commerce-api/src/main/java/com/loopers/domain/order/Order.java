package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    private static final String STATUS_ORDERED = "ORDERED";

    private Long memberId;
    private String status;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "original_amount", nullable = false)
    private long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineEntity> orderLines = new ArrayList<>();

    protected Order() {}

    private Order(Long memberId, List<OrderLine> orderLines, Long couponId, long discountAmount) {
        validateMemberId(memberId);
        validateOrderLines(orderLines);
        this.memberId = memberId;
        this.status = STATUS_ORDERED;
        this.couponId = couponId;
        for (OrderLine line : orderLines) {
            this.orderLines.add(new OrderLineEntity(this, line.productId(), line.quantity(), line.unitPrice()));
        }
        this.originalAmount = calculateOriginalAmount();
        this.discountAmount = Math.min(discountAmount, this.originalAmount);
    }

    public static Order create(Long memberId, List<OrderLine> orderLines) {
        return new Order(memberId, orderLines, null, 0);
    }

    public static Order createWithCoupon(Long memberId, List<OrderLine> orderLines, Long couponId, long discountAmount) {
        return new Order(memberId, orderLines, couponId, discountAmount);
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
    }

    private void validateOrderLines(List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.");
        }
    }

    private long calculateOriginalAmount() {
        return orderLines.stream()
            .mapToLong(OrderLineEntity::getTotalPrice)
            .sum();
    }

    public Long getMemberId() { return memberId; }
    public String getStatus() { return status; }
    public Long getCouponId() { return couponId; }
    public long getOriginalAmount() { return originalAmount; }
    public long getDiscountAmount() { return discountAmount; }

    public long getTotalAmount() {
        return originalAmount - discountAmount;
    }

    public List<OrderLineEntity> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "order_line")
    public static class OrderLineEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
        @jakarta.persistence.JoinColumn(name = "order_id", nullable = false)
        private Order order;

        @jakarta.persistence.Column(name = "product_id", nullable = false)
        private Long productId;

        @jakarta.persistence.Column(nullable = false)
        private int quantity;

        @jakarta.persistence.Column(name = "unit_price", nullable = false)
        private long unitPrice;

        protected OrderLineEntity() {}

        OrderLineEntity(Order order, Long productId, int quantity, long unitPrice) {
            this.order = order;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public long getTotalPrice() {
            return (long) quantity * unitPrice;
        }

        public Long getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public long getUnitPrice() { return unitPrice; }
    }
}
