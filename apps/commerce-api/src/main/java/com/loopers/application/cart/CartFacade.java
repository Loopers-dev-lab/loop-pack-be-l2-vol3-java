package com.loopers.application.cart;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

        if (cartItems.isEmpty()) {
            return CartInfo.of(List.of());
        }

        List<Long> optionIds = cartItems.stream().map(CartItem::getOptionId).toList();
        Map<Long, Option> optionMap = productAppService.getOptionsByIds(optionIds);

        List<Long> productIds = optionMap.values().stream()
                .map(Option::getProductId)
                .distinct()
                .toList();
        Map<Long, Product> productMap = productAppService.getByIds(productIds);

        List<CartInfo.CartItemInfo> itemInfos = cartItems.stream()
                .map(cartItem -> {
                    Option option = optionMap.get(cartItem.getOptionId());
                    Product product = productMap.get(option.getProductId());
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
