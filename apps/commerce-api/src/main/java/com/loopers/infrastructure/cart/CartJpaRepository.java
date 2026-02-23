package com.loopers.infrastructure.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<CartItemJpaEntity, Long> {
    Optional<CartItemJpaEntity> findByUserIdAndOptionId(Long userId, Long optionId);
    List<CartItemJpaEntity> findByUserId(Long userId);
    List<CartItemJpaEntity> findByIdIn(List<Long> ids);
    void deleteByIdIn(List<Long> ids);
}
