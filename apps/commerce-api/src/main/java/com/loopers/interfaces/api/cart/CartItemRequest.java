package com.loopers.interfaces.api.cart;

public class CartItemRequest {

    public record AddCartItemRequest(
            Long productId,
            int quantity
    ) {}

    public record ChangeQuantityRequest(
            int quantity
    ) {}
}
