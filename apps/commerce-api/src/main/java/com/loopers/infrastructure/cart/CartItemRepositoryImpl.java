package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CartItemRepositoryImpl implements CartItemRepository {

    private final CartItemJpaRepository cartItemJpaRepository;
    private final CartItemMapper cartItemMapper;

    public CartItemRepositoryImpl(
        CartItemJpaRepository cartItemJpaRepository,
        CartItemMapper cartItemMapper
    ) {
        this.cartItemJpaRepository = cartItemJpaRepository;
        this.cartItemMapper = cartItemMapper;
    }

    @Override
    public CartItem save(CartItem cartItem) {
        CartItemEntity entity = cartItemMapper.toEntity(cartItem);
        CartItemEntity saved = cartItemJpaRepository.save(entity);
        return cartItemMapper.toDomain(saved);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartItemJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(cartItemMapper::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemJpaRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId)
            .map(cartItemMapper::toDomain);
    }

    @Override
    public List<CartItem> findAllByUserId(Long userId) {
        return cartItemJpaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
            .stream()
            .map(cartItemMapper::toDomain)
            .toList();
    }

    @Override
    public List<CartItem> findAllByProductId(Long productId) {
        return cartItemJpaRepository.findAllByProductIdAndDeletedAtIsNull(productId)
            .stream()
            .map(cartItemMapper::toDomain)
            .toList();
    }

    @Override
    public List<CartItem> findAllByIdIn(List<Long> ids) {
        return cartItemJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
            .stream()
            .map(cartItemMapper::toDomain)
            .toList();
    }
}
