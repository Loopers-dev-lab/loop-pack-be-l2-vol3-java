package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class CartDomainService {

    private final CartRepository cartRepository;

    public void addToCart(Long userId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        cart.addItem(productId, quantity);
        cartRepository.save(cart);
    }

    public void updateItemQuantity(Long userId, Long cartItemId, int quantity) {
        Cart cart = getCartByUserId(userId);
        cart.updateItemQuantity(cartItemId, quantity);
        cartRepository.save(cart);
    }

    public void removeItem(Long userId, Long cartItemId) {
        Cart cart = getCartByUserId(userId);
        cart.removeItem(cartItemId);
        cartRepository.save(cart);
    }

    public List<CartItem> getCartItems(Long userId) {
        return cartRepository.findByUserId(userId)
            .map(Cart::getItems)
            .orElse(Collections.emptyList());
    }

    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.clear();
            cartRepository.save(cart);
        });
    }

    public void removeUnavailableItems(Long userId, Set<Long> availableProductIds) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.removeUnavailableItems(availableProductIds);
            cartRepository.save(cart);
        });
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> new Cart(userId));
    }

    private Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "장바구니를 찾을 수 없습니다."));
    }
}
