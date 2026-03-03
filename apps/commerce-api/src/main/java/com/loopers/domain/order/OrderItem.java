package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {}

    private OrderItem(Long orderId, Long productId, String productName, long unitPrice, Integer quantity) {
        validateQuantity(quantity);
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(Long orderId, Long productId, String productName, long price, Integer quantity) {
        return new OrderItem(orderId, productId, productName, price, quantity);
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1개 이상이어야 합니다.");
        }
    }
}
