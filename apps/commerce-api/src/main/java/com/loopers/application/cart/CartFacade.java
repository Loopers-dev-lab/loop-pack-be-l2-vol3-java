package com.loopers.application.cart;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CartFacade {
    private final CartAppService cartAppService;
    private final ProductAppService productAppService;

    public CartItem addToCart(Long userId, Long optionId, int quantity) {
        productAppService.getOptionById(optionId);
        return cartAppService.addToCart(userId, optionId, quantity);
    }

    public CartInfo getCart(Long userId) {
        List<CartItem> cartItems = cartAppService.getCartItems(userId);

        List<CartInfo.CartItemInfo> itemInfos = cartItems.stream()
                .map(cartItem -> {
                    Option option = productAppService.getOptionById(cartItem.getOptionId());
                    Product product = productAppService.getById(option.getProductId());
                    return CartInfo.CartItemInfo.of(cartItem, product, option);
                })
                .toList();

        return CartInfo.of(itemInfos);
    }

    public CartItem updateQuantity(Long userId, Long cartItemId, int quantity) {
        return cartAppService.updateQuantity(userId, cartItemId, quantity);
    }

    public void delete(Long userId, Long cartItemId) {
        cartAppService.delete(userId, cartItemId);
    }
}
