package com.loopers.domain.cart;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface CartRepository {

    List<CartItemModel> findByUserId(Long userId);

    Optional<CartItemModel> findByUserIdAndCartItemId(Long userId, Long cartItemId);

    CartItemModel save(CartItemModel cartItem);

    void delete(CartItemModel cartItem);
}
