package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

@Entity
@Table(name = "order_item")
public class OrderItemModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "product_price", nullable = false))
    private Money productPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItemModel() {
    }

    public OrderItemModel(Long orderId, Long productId, String productName, Money productPrice, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        guard();
    }

    @Override
    protected void guard() {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 정보는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 정보는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (productPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }

    public Money subtotal() {
        return productPrice.multiply(quantity);
    }

    public Long orderId() {
        return orderId;
    }

    public Long productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public Money productPrice() {
        return productPrice;
    }

    public int quantity() {
        return quantity;
    }
}
