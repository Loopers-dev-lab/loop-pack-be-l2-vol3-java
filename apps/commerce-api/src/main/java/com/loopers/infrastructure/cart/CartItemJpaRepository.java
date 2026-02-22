package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByIdAndDeletedAtIsNull(Long id);

    Optional<CartItem> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId);

    List<CartItem> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
}
