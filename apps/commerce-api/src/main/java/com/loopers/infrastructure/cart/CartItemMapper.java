package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.vo.Quantity;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * CartItemMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class CartItemMapper {

    /**
     * Domain → JPA Entity
     */
    public CartItemEntity toEntity(CartItem cartItem) {
        CartItemEntity entity = new CartItemEntity();
        entity.setId(cartItem.getId());
        entity.setUserId(cartItem.getUserId());
        entity.setProductId(cartItem.getProductId());
        entity.setQuantity(cartItem.getQuantity()); // Quantity → Integer
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(cartItem.getCreatedAt() != null ? cartItem.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(cartItem.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public CartItem toDomain(CartItemEntity entity) {
        return CartItem.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getProductId(),
            new Quantity(entity.getQuantity()), // Integer → Quantity
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }
}
