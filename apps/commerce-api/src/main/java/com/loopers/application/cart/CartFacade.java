package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CartFacade {

    private final CartService cartService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public void addToCart(Long userId, Long productId, int quantity) {
        productService.getById(productId);
        cartService.addToCart(userId, productId, quantity);
    }

    @Transactional(readOnly = true)
    public List<CartInfo> getMyCart(Long userId) {
        List<CartItem> cartItems = cartService.getCartItems(userId);

        Set<Long> productIds = cartItems.stream()
            .map(CartItem::getProductId)
            .collect(Collectors.toSet());

        Map<Long, Product> productMap = productService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        return cartItems.stream()
            .filter(cartItem -> productMap.containsKey(cartItem.getProductId()))
            .map(cartItem -> {
                Product product = productMap.get(cartItem.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return CartInfo.from(cartItem, product, brand);
            })
            .toList();
    }

    @Transactional
    public void updateQuantity(Long cartItemId, Long userId, int quantity) {
        cartService.updateQuantity(cartItemId, userId, quantity);
    }

    @Transactional
    public void removeItem(Long cartItemId, Long userId) {
        cartService.removeItem(cartItemId, userId);
    }
}
