package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartAppService {
    private final CartRepository cartRepository;

    @Transactional
    public CartItem addToCart(Long userId, Long optionId, int quantity) {
        Optional<CartItem> existing = cartRepository.findByUserIdAndOptionId(userId, optionId);
        if (existing.isPresent()) {
            CartItem cartItem = existing.get();
            cartItem.addQuantity(quantity);
            return cartRepository.save(cartItem);
        }
        CartItem cartItem = CartItem.create(userId, optionId, quantity);
        return cartRepository.save(cartItem);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public CartItem getById(Long id) {
        return cartRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<CartItem> getByIds(List<Long> ids) {
        return cartRepository.findByIds(ids);
    }

    @Transactional
    public CartItem updateQuantity(Long userId, Long cartItemId, int quantity) {
        CartItem cartItem = getById(cartItemId);
        cartItem.validateOwner(userId);
        cartItem.updateQuantity(quantity);
        return cartRepository.save(cartItem);
    }

    @Transactional
    public void delete(Long userId, Long cartItemId) {
        CartItem cartItem = getById(cartItemId);
        cartItem.validateOwner(userId);
        cartRepository.delete(cartItem);
    }

    @Transactional
    public void deleteByIds(List<Long> ids) {
        cartRepository.deleteByIds(ids);
    }
}
