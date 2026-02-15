package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartJpaRepository.save(cartItem);
    }

    @Override
    public void delete(CartItem cartItem) {
        cartJpaRepository.delete(cartItem);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartJpaRepository.deleteAllByUserId(userId);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartJpaRepository.findById(id);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<CartItem> findAllByUserId(Long userId) {
        return cartJpaRepository.findAllByUserId(userId);
    }
}
