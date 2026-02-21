package com.loopers.domain.cart;

import java.util.Optional;

public interface CartItemRepository {
    CartItem save(CartItem cartItem);
    Optional<CartItem> findById(Long id);
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    void delete(CartItem cartItem);
}
