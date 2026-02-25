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
        return cartItemRepository.findByUserIdAndProductIdIncludeDeleted(userId, productId)
                .map(existing -> {
                    if (existing.getDeletedAt() != null) {
                        existing.restore(quantity);
                    } else {
                        existing.addQuantity(quantity);
                    }
                    return cartItemRepository.save(existing);
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

    /** ID 목록으로 장바구니 아이템 조회 + 소유권 검증 */
    @Transactional(readOnly = true)
    public List<CartItem> getCartItemsByIds(List<Long> cartItemIds, Long userId) {
        List<CartItem> cartItems = cartItemRepository.findAllByIdIn(cartItemIds);
        if (cartItems.size() != cartItemIds.size()) {
            throw new CoreException(CartItemErrorType.CART_ITEM_NOT_FOUND);
        }
        cartItems.forEach(item -> item.validateOwnership(userId));
        return cartItems;
    }

    /** 장바구니 아이템 일괄 소프트 삭제 (주문 전환 시) */
    @Transactional
    public void deleteAll(List<Long> cartItemIds, Long userId) {
        List<CartItem> cartItems = cartItemRepository.findAllByIdIn(cartItemIds);
        cartItems.forEach(item -> {
            item.validateOwnership(userId);
            item.delete();
        });
    }

    /** 상품 삭제 시 해당 상품을 참조하는 장바구니 아이템 일괄 소프트 삭제 */
    @Transactional
    public void deleteByProductId(Long productId) {
        List<CartItem> cartItems = cartItemRepository.findAllByProductId(productId);
        cartItems.forEach(CartItem::delete);
    }
}
