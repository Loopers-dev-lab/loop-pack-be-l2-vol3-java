package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "status", nullable = false)
    private String status;

    protected Order() {}

    public Order(Long userId, Money totalPrice) {
        validate(userId, totalPrice);
        this.userId = userId;
        this.totalPrice = totalPrice.amount();
        this.status = OrderStatus.ORDERED.name();
    }

    public Long getUserId() { return userId; }
    public Money getTotalPrice() { return new Money(totalPrice); }
    public OrderStatus getStatus() { return OrderStatus.valueOf(status); }

    private void validate(Long userId, Money totalPrice) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (totalPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 필수입니다.");
        }
    }
}
