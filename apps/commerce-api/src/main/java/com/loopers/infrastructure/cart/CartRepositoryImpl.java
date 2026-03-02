package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CartRepositoryImpl implements CartRepository {

    private final CartItemJpaRepository cartItemJpaRepository;

    public CartRepositoryImpl(CartItemJpaRepository cartItemJpaRepository) {
        this.cartItemJpaRepository = cartItemJpaRepository;
    }

    @Override
    public List<CartItemModel> findByUserId(Long userId) {
        return cartItemJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<CartItemModel> findByUserIdAndCartItemId(Long userId, Long cartItemId) {
        return cartItemJpaRepository.findByIdAndUserId(cartItemId, userId);
    }

    @Override
    public CartItemModel save(CartItemModel cartItem) {
        return cartItemJpaRepository.save(cartItem);
    }

    @Override
    public void delete(CartItemModel cartItem) {
        cartItemJpaRepository.delete(cartItem);
    }
}
