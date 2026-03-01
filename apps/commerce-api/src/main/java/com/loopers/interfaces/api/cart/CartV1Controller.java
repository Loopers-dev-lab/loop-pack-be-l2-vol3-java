package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartApplicationService;
import com.loopers.application.cart.CartItemDetail;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/cart")
public class CartV1Controller implements CartV1ApiSpec {

    private final CartApplicationService cartApplicationService;

    @PostMapping("/items")
    @Override
    public ApiResponse<Void> addToCart(@AuthUser AuthenticatedUser authUser, @Valid @RequestBody CartV1Dto.AddRequest request) {
        cartApplicationService.addToCart(authUser.userId(), request.productId(), request.quantity());
        return ApiResponse.success();
    }

    @GetMapping
    @Override
    public ApiResponse<CartV1Dto.CartResponse> getMyCart(@AuthUser AuthenticatedUser authUser) {
        List<CartItemDetail> details = cartApplicationService.getMyCartWithDetails(authUser.userId());

        List<CartV1Dto.CartItemResponse> itemResponses = details.stream()
            .map(detail -> CartV1Dto.CartItemResponse.from(detail.cartItem(), detail.product(), detail.brand()))
            .toList();

        return ApiResponse.success(CartV1Dto.CartResponse.from(itemResponses));
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Void> updateQuantity(
        @AuthUser AuthenticatedUser authUser,
        @PathVariable Long cartItemId,
        @Valid @RequestBody CartV1Dto.UpdateQuantityRequest request
    ) {
        cartApplicationService.updateQuantity(cartItemId, authUser.userId(), request.quantity());
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Void> removeItem(@AuthUser AuthenticatedUser authUser, @PathVariable Long cartItemId) {
        cartApplicationService.removeItem(cartItemId, authUser.userId());
        return ApiResponse.success();
    }
}
