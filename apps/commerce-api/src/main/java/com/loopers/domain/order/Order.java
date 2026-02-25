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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, Money totalPrice) {
        validate(userId, totalPrice);
        this.userId = userId;
        this.totalPrice = totalPrice.amount();
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

    public void cancel() {
        if (this.status != OrderStatus.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 취소가 불가능한 상태입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getUserId() { return userId; }
    public Money getTotalPrice() { return new Money(totalPrice); }
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
