package com.loopers.domain.cart;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Quantity;
import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    protected CartItem() {}

    private CartItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new CoreException(CartItemErrorType.INVALID_QUANTITY);
        }
        this.userId = userId;
        this.productId = productId;
        this.quantity = new Quantity(quantity);
    }

    public static CartItem create(Long userId, Long productId, int quantity) {
        return new CartItem(userId, productId, quantity);
    }

    public void addQuantity(int additionalQuantity) {
        this.quantity = this.quantity.plus(new Quantity(additionalQuantity));
    }

    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(CartItemErrorType.INVALID_QUANTITY);
        }
        this.quantity = new Quantity(quantity);
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(CartItemErrorType.NOT_OWNER);
        }
    }

    public Long getUserId() {
        return this.userId;
    }

    public Long getProductId() {
        return this.productId;
    }

    public int getQuantity() {
        return this.quantity.toInt();
    }
}
