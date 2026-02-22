package com.loopers.domain.cart;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public CartItemService(CartItemRepository cartItemRepository, ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
    }

    @Transactional
    public CartItem addToCart(Long userId, Long productId, int quantity) {
        Product product = productService.getDisplayableProduct(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new CoreException(CartItemErrorType.NOT_PURCHASABLE);
        }

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
