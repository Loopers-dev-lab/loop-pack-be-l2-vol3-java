package com.loopers.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartRepository {
    CartItem save(CartItem cartItem);
    Optional<CartItem> findById(Long id);
    Optional<CartItem> findByUserIdAndOptionId(Long userId, Long optionId);
    List<CartItem> findByUserId(Long userId);
    List<CartItem> findByIds(List<Long> ids);
    void delete(CartItem cartItem);
    void deleteByIds(List<Long> ids);
}
