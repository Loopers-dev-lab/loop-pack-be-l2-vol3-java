package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {
    private final CartJpaRepository cartJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return cartJpaRepository.save(cartItem);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartJpaRepository.findById(id);
    }

    @Override
    public Optional<CartItem> findByUserIdAndOptionId(Long userId, Long optionId) {
        return cartJpaRepository.findByUserIdAndOptionId(userId, optionId);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return cartJpaRepository.findByUserId(userId);
    }

    @Override
    public List<CartItem> findByIds(List<Long> ids) {
        return cartJpaRepository.findByIdIn(ids);
    }

    @Override
    public void delete(CartItem cartItem) {
        cartJpaRepository.delete(cartItem);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        cartJpaRepository.deleteByIdIn(ids);
    }
}
