package com.loopers.application.cart;

import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → CartInfo 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 */
@Service
public class CartFacade {

    private final CartService cartService;

    public CartFacade(CartService cartService) {
        this.cartService = cartService;
    }

    @Transactional
    public CartInfo addItem(Long userId, Long productId, Long optionId, int quantity) {
        CartItemModel item = cartService.addItem(userId, productId, optionId, quantity);
        return CartInfo.from(item);
    }

    @Transactional(readOnly = true)
    public List<CartInfo> getItems(Long userId) {
        return cartService.getItems(userId).stream()
            .map(CartInfo::from)
            .toList();
    }

    @Transactional
    public CartInfo updateItem(Long userId, Long cartItemId, int quantity, Long optionId) {
        CartItemModel item = cartService.updateItem(userId, cartItemId, quantity, optionId);
        return CartInfo.from(item);
    }

    @Transactional
    public void removeItems(Long userId, List<Long> cartItemIds) {
        cartService.removeItems(userId, cartItemIds);
    }
}
