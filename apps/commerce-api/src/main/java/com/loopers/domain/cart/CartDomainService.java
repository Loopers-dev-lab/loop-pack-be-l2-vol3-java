package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class CartDomainService {

    private final CartRepository cartRepository;

    public CartItem addToCart(Long userId, Long productId, int quantity) {
        Optional<CartItem> existing = cartRepository.findByUserIdAndProductId(userId, productId);

        if (existing.isPresent()) {
            CartItem cartItem = existing.get();
            cartItem.addQuantity(quantity);
            return cartRepository.save(cartItem);
        }

        return cartRepository.save(new CartItem(userId, productId, quantity));
    }

    public CartItem updateQuantity(Long cartItemId, Long userId, int quantity) {
        CartItem cartItem = getByIdAndUserId(cartItemId, userId);
        cartItem.updateQuantity(quantity);
        return cartRepository.save(cartItem);
    }

    public void removeItem(Long cartItemId, Long userId) {
        CartItem cartItem = getByIdAndUserId(cartItemId, userId);
        cartRepository.delete(cartItem);
    }

    public List<CartItem> getCartItems(Long userId) {
        return cartRepository.findAllByUserId(userId);
    }

    public void clearCart(Long userId) {
        cartRepository.deleteAllByUserId(userId);
    }

    private CartItem getByIdAndUserId(Long cartItemId, Long userId) {
        CartItem cartItem = cartRepository.findById(cartItemId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."));

        if (!cartItem.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다.");
        }

        return cartItem;
    }
}
