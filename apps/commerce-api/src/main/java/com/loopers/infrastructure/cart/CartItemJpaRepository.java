package com.loopers.infrastructure.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, Long> {

    Optional<CartItemEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<CartItemEntity> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId);

    List<CartItemEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<CartItemEntity> findAllByProductIdAndDeletedAtIsNull(Long productId);

    List<CartItemEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
