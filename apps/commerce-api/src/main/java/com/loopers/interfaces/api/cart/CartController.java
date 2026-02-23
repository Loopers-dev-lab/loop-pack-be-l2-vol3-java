package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartFacade cartFacade;

    @PostMapping
    public ApiResponse<CartDto.AddResponse> addToCart(
            @LoginUser Member member,
            @RequestBody CartDto.AddRequest request
    ) {
        CartItem cartItem = cartFacade.addToCart(member.getId(), request.optionId(), request.quantity());
        return ApiResponse.success(CartDto.AddResponse.from(cartItem));
    }

    @GetMapping
    public ApiResponse<CartDto.CartResponse> getCart(@LoginUser Member member) {
        CartInfo cartInfo = cartFacade.getCart(member.getId());
        return ApiResponse.success(CartDto.CartResponse.from(cartInfo));
    }

    @PatchMapping("/{itemId}")
    public ApiResponse<CartDto.AddResponse> updateQuantity(
            @LoginUser Member member,
            @PathVariable Long itemId,
            @RequestBody CartDto.UpdateQuantityRequest request
    ) {
        CartItem cartItem = cartFacade.updateQuantity(member.getId(), itemId, request.quantity());
        return ApiResponse.success(CartDto.AddResponse.from(cartItem));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(
            @LoginUser Member member,
            @PathVariable Long itemId
    ) {
        cartFacade.delete(member.getId(), itemId);
        return ApiResponse.success(null);
    }
}
