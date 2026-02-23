package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartItemFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
public class CartItemController implements CartItemApiSpec {

    private final CartItemFacade cartItemFacade;

    public CartItemController(CartItemFacade cartItemFacade) {
        this.cartItemFacade = cartItemFacade;
    }

    @GetMapping
    @Override
    public ApiResponse<CartItemResponse.CartListResponse> getCart(@AuthUser User user) {
        CartItemFacade.CartListResult result = cartItemFacade.getCart(user.getId());

        List<CartItemResponse.CartItemSummary> summaries = result.items().stream()
                .map(item -> new CartItemResponse.CartItemSummary(
                        item.cartItemId(), item.productId(), item.productName(),
                        item.brandName(), item.basePrice(), item.quantity(),
                        item.availableStock(), item.productStatus()))
                .toList();

        return ApiResponse.success(new CartItemResponse.CartListResponse(summaries));
    }

    @PostMapping("/items")
    @Override
    public ApiResponse<Object> addToCart(@AuthUser User user,
                                         @RequestBody CartItemRequest.AddCartItemRequest request) {
        cartItemFacade.addToCart(user.getId(), request.productId(), request.quantity());
        return ApiResponse.success();
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> changeQuantity(@AuthUser User user,
                                               @PathVariable Long cartItemId,
                                               @RequestBody CartItemRequest.ChangeQuantityRequest request) {
        cartItemFacade.changeQuantity(cartItemId, user.getId(), request.quantity());
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> deleteCartItem(@AuthUser User user,
                                               @PathVariable Long cartItemId) {
        cartItemFacade.deleteCartItem(cartItemId, user.getId());
        return ApiResponse.success();
    }
}
