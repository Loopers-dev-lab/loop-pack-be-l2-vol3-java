package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Product;

public record CartInfo(
    Long cartItemId,
    Long productId,
    String productName,
    String brandName,
    int price,
    int quantity
) {
    public static CartInfo from(CartItem cartItem, Product product, Brand brand) {
        return new CartInfo(
            cartItem.getId(),
            product.getId(),
            product.getName(),
            brand.getName(),
            product.getPrice().amount(),
            cartItem.getQuantity().value()
        );
    }
}
