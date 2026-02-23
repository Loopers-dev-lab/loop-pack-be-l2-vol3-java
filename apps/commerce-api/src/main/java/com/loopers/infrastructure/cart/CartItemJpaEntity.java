package com.loopers.infrastructure.cart;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.cart.CartItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "option_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private CartItemJpaEntity(Long userId, Long optionId, int quantity) {
        this.userId = userId;
        this.optionId = optionId;
        this.quantity = quantity;
    }

    public static CartItemJpaEntity from(CartItem cartItem) {
        return new CartItemJpaEntity(
                cartItem.getUserId(),
                cartItem.getOptionId(),
                cartItem.getQuantity()
        );
    }

    public CartItem toDomain() {
        return CartItem.of(getId(), userId, optionId, quantity);
    }

    public void update(CartItem cartItem) {
        this.quantity = cartItem.getQuantity();
    }
}
