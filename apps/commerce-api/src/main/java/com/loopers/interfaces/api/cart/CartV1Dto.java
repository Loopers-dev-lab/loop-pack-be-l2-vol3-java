package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CartV1Dto {

    public record AddRequest(
        @NotNull Long productId,
        @Min(1) int quantity
    ) {}

    public record UpdateQuantityRequest(
        @Min(1) int quantity
    ) {}

    public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String brandName,
        int price,
        int quantity
    ) {
        public static CartItemResponse from(CartInfo info) {
            return new CartItemResponse(
                info.cartItemId(), info.productId(), info.productName(),
                info.brandName(), info.price(), info.quantity()
            );
        }
    }

    public record CartResponse(List<CartItemResponse> items) {
        public static CartResponse from(List<CartInfo> infos) {
            List<CartItemResponse> items = infos.stream()
                .map(CartItemResponse::from)
                .toList();
            return new CartResponse(items);
        }
    }
}
