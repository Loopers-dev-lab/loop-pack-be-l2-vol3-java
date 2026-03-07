package com.loopers.domain.cart;

import com.loopers.domain.common.vo.Quantity;
import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;

/**
 * 장바구니 아이템 엔티티 (Aggregate Root) - 순수 POJO
 */
public class CartItem {

    private Long id;
    private Long userId;
    private Long productId;
    private Quantity quantity;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected CartItem() {}

    private CartItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new CoreException(CartItemErrorType.INVALID_QUANTITY);
        }
        this.userId = userId;
        this.productId = productId;
        this.quantity = new Quantity(quantity);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static CartItem reconstitute(
        Long id,
        Long userId,
        Long productId,
        Quantity quantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        CartItem cartItem = new CartItem();
        cartItem.id = id;
        cartItem.userId = userId;
        cartItem.productId = productId;
        cartItem.quantity = quantity;
        cartItem.createdAt = createdAt;
        cartItem.updatedAt = updatedAt;
        cartItem.deletedAt = deletedAt;
        return cartItem;
    }

    public static CartItem of(Long userId, Long productId, int quantity) {
        CartItem cartItem = new CartItem(userId, productId, quantity);
        ZonedDateTime now = ZonedDateTime.now();
        cartItem.createdAt = now;
        cartItem.updatedAt = now;
        return cartItem;
    }

    public void addQuantity(int additionalQuantity) {
        this.quantity = this.quantity.plus(new Quantity(additionalQuantity));
        this.updatedAt = ZonedDateTime.now();
    }

    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(CartItemErrorType.INVALID_QUANTITY);
        }
        this.quantity = new Quantity(quantity);
        this.updatedAt = ZonedDateTime.now();
    }

    public void remove() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** 소프트 삭제된 장바구니 아이템 복구 (deletedAt 초기화 + 수량 재설정) */
    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(CartItemErrorType.INVALID_QUANTITY);
        }
        this.quantity = new Quantity(quantity);
        this.deletedAt = null;
        this.updatedAt = ZonedDateTime.now();
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(CartItemErrorType.NOT_OWNER);
        }
    }

    public Long getId() {
        return this.id;
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

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
