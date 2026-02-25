package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItemModel, Long> {

    List<CartItemModel> findByUserId(Long userId);

    Optional<CartItemModel> findByIdAndUserId(Long id, Long userId);
}
