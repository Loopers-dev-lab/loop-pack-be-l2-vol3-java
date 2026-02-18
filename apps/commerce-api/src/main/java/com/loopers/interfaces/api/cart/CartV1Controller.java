package com.loopers.interfaces.api.cart;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.cart.CartApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/cart")
public class CartV1Controller implements CartV1ApiSpec {

    private final CartApplicationService cartApplicationService;
    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    @PostMapping("/items")
    @Override
    public ApiResponse<Void> addToCart(@AuthUser User user, @Valid @RequestBody CartV1Dto.AddRequest request) {
        cartApplicationService.addToCart(user.getId(), request.productId(), request.quantity());
        return ApiResponse.success();
    }

    @GetMapping
    @Override
    public ApiResponse<CartV1Dto.CartResponse> getMyCart(@AuthUser User user) {
        List<CartItem> cartItems = cartApplicationService.getMyCart(user.getId());

        Set<Long> productIds = cartItems.stream()
            .map(CartItem::getProductId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productApplicationService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandApplicationService.getByIds(brandIds);

        List<CartV1Dto.CartItemResponse> itemResponses = cartItems.stream()
            .filter(cartItem -> {
                Product product = productMap.get(cartItem.getProductId());
                return product != null && brandMap.containsKey(product.getBrandId());
            })
            .map(cartItem -> {
                Product product = productMap.get(cartItem.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return CartV1Dto.CartItemResponse.from(cartItem, product, brand);
            })
            .toList();

        return ApiResponse.success(CartV1Dto.CartResponse.from(itemResponses));
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Void> updateQuantity(
        @AuthUser User user,
        @PathVariable Long cartItemId,
        @Valid @RequestBody CartV1Dto.UpdateQuantityRequest request
    ) {
        cartApplicationService.updateQuantity(cartItemId, user.getId(), request.quantity());
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Void> removeItem(@AuthUser User user, @PathVariable Long cartItemId) {
        cartApplicationService.removeItem(cartItemId, user.getId());
        return ApiResponse.success();
    }
}
