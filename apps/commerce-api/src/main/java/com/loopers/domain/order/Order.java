package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "original_price", nullable = false)
    private int originalPrice;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "coupon_issue_id")
    private Long couponIssueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    protected Order() {}

    public Order(Long userId, Money totalPrice) {
        validate(userId, totalPrice);
        this.userId = userId;
        this.totalPrice = totalPrice.amount();
        this.originalPrice = totalPrice.amount();
        this.discountAmount = 0;
        this.status = OrderStatus.ORDERED;
    }

    public Order(Long userId, Money originalPrice, Money discountAmount, Long couponIssueId) {
        validate(userId, originalPrice);
        this.userId = userId;
        this.originalPrice = originalPrice.amount();
        this.discountAmount = discountAmount.amount();
        this.totalPrice = originalPrice.minus(discountAmount).amount();
        this.couponIssueId = couponIssueId;
        this.status = OrderStatus.ORDERED;
    }

    public void addItems(List<OrderItemCommand> commands) {
        for (OrderItemCommand cmd : commands) {
            this.items.add(new OrderItem(
                this, cmd.productId(), cmd.productName(),
                cmd.productPrice(), cmd.brandName(), cmd.quantity()
            ));
        }
    }

    public void startPayment() {
        if (this.status != OrderStatus.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "결제를 시작할 수 없는 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public void completePayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "결제 완료 처리할 수 없는 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.PAID;
    }

    public void failPayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "결제 실패 처리할 수 없는 상태입니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (this.status != OrderStatus.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 취소가 불가능한 상태입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getUserId() { return userId; }
    public Money getTotalPrice() { return new Money(totalPrice); }
    public Money getOriginalPrice() { return new Money(originalPrice); }
    public Money getDiscountAmount() { return new Money(discountAmount); }
    public Long getCouponIssueId() { return couponIssueId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }

    private void validate(Long userId, Money totalPrice) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (totalPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 필수입니다.");
        }
    }
}
