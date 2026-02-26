package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByUserIdAndOptionId(Long userId, Long optionId);
    List<CartItem> findByUserId(Long userId);
    List<CartItem> findByIdIn(List<Long> ids);
    void deleteByIdIn(List<Long> ids);
}
