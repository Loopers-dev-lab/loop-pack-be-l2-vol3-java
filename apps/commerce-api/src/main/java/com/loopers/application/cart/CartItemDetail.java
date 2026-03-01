package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Product;

public record CartItemDetail(CartItem cartItem, Product product, Brand brand) {
}
