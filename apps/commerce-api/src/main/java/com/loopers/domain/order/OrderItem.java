package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_item_order_id", columnList = "order_id"),
        @Index(name = "idx_order_item_product_id", columnList = "product_id")
    }
)
@Getter
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal productPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    private OrderItem(Long productId, String productName, BigDecimal productPrice, Integer quantity, BigDecimal subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public static OrderItem createSnapshot(Long productId, String productName, BigDecimal productPrice, Integer quantity) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (productPrice == null || productPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다");
        }

        BigDecimal subtotal = productPrice.multiply(BigDecimal.valueOf(quantity));
        return new OrderItem(productId, productName, productPrice, quantity, subtotal);
    }

    void assignOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문 정보는 필수입니다");
        }
        this.order = order;
    }

    @Override
    protected void guard() {
        if (this.order == null) {
            throw new IllegalArgumentException("주문 정보는 필수입니다");
        }
        if (this.productId == null || this.productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        if (this.productName == null || this.productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (this.productName.length() > 200) {
            throw new IllegalArgumentException("상품명은 200자 이하여야 합니다");
        }
        if (this.productPrice == null || this.productPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다");
        }
        if (this.quantity == null || this.quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다");
        }
        if (this.subtotal == null || this.subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다");
        }
    }
}
