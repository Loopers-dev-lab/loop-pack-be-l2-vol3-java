package com.loopers.domain.cart;

import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CartItemService {

    private final CartItemRepository cartItemRepository;

    public CartItemService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
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

    @Transactional
    public void changeQuantity(Long cartItemId, Long userId, int quantity) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CoreException(CartItemErrorType.CART_ITEM_NOT_FOUND));
        cartItem.validateOwnership(userId);
        cartItem.changeQuantity(quantity);
    }

    @Transactional
    public void delete(Long cartItemId, Long userId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CoreException(CartItemErrorType.CART_ITEM_NOT_FOUND));
        cartItem.validateOwnership(userId);
        cartItem.delete();
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findAllByUserId(userId);
    }
}
