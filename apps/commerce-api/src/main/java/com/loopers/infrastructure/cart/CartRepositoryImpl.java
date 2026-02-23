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
        if (cartItem.getId() == null) {
            CartItemJpaEntity entity = CartItemJpaEntity.from(cartItem);
            CartItemJpaEntity saved = cartJpaRepository.save(entity);
            return saved.toDomain();
        }

        CartItemJpaEntity entity = cartJpaRepository.findById(cartItem.getId())
                .orElseThrow(() -> new IllegalStateException("CartItem not found: " + cartItem.getId()));
        entity.update(cartItem);
        return entity.toDomain();
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartJpaRepository.findById(id)
                .map(CartItemJpaEntity::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndOptionId(Long userId, Long optionId) {
        return cartJpaRepository.findByUserIdAndOptionId(userId, optionId)
                .map(CartItemJpaEntity::toDomain);
    }

    @Override
    public List<CartItem> findByUserId(Long userId) {
        return cartJpaRepository.findByUserId(userId).stream()
                .map(CartItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<CartItem> findByIds(List<Long> ids) {
        return cartJpaRepository.findByIdIn(ids).stream()
                .map(CartItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void delete(CartItem cartItem) {
        cartJpaRepository.findById(cartItem.getId())
                .ifPresent(cartJpaRepository::delete);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        cartJpaRepository.deleteByIdIn(ids);
    }
}
