package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/cart")
public class CartV1Controller implements CartV1ApiSpec {

    private final CartFacade cartFacade;

    @PostMapping("/items")
    @Override
    public ApiResponse<Object> addToCart(@AuthUser User user, @Valid @RequestBody CartV1Dto.AddRequest request) {
        cartFacade.addToCart(user.getId(), request.productId(), request.quantity());
        return ApiResponse.success();
    }

    @GetMapping
    @Override
    public ApiResponse<CartV1Dto.CartResponse> getMyCart(@AuthUser User user) {
        List<CartInfo> infos = cartFacade.getMyCart(user.getId());
        return ApiResponse.success(CartV1Dto.CartResponse.from(infos));
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> updateQuantity(
        @AuthUser User user,
        @PathVariable Long cartItemId,
        @Valid @RequestBody CartV1Dto.UpdateQuantityRequest request
    ) {
        cartFacade.updateQuantity(cartItemId, user.getId(), request.quantity());
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> removeItem(@AuthUser User user, @PathVariable Long cartItemId) {
        cartFacade.removeItem(cartItemId, user.getId());
        return ApiResponse.success();
    }
}
