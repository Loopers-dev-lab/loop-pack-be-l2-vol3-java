package com.loopers.domain.cart;

import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
public class CartItemService {

    private final CartItemRepository cartItemRepository;

    public CartItemService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    public CartItem addToCart(Long userId, Long productId, int quantity) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(existing -> {
                    existing.addQuantity(quantity);
                    return existing;
                })
                .orElseGet(() -> {
                    CartItem cartItem = CartItem.create(userId, productId, quantity);
                    return cartItemRepository.save(cartItem);
                });
    }

    public void changeQuantity(Long cartItemId, Long userId, int quantity) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CoreException(CartItemErrorType.CART_ITEM_NOT_FOUND));
        cartItem.validateOwnership(userId);
        cartItem.changeQuantity(quantity);
    }

    public void delete(Long cartItemId, Long userId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CoreException(CartItemErrorType.CART_ITEM_NOT_FOUND));
        cartItem.validateOwnership(userId);
        cartItemRepository.delete(cartItem);
    }
}
