package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CartApplicationService {

    private final CartDomainService cartService;
    private final ProductDomainService productService;

    @Transactional
    public void addToCart(Long userId, Long productId, int quantity) {
        productService.getById(productId);
        cartService.addToCart(userId, productId, quantity);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getMyCart(Long userId) {
        return cartService.getCartItems(userId);
    }

    @Transactional
    public void updateQuantity(Long cartItemId, Long userId, int quantity) {
        cartService.updateItemQuantity(userId, cartItemId, quantity);
    }

    @Transactional
    public void removeItem(Long cartItemId, Long userId) {
        cartService.removeItem(userId, cartItemId);
    }
}
