package com.loopers.interfaces.api.cart;

import java.util.List;

public class CartItemResponse {

    public record CartItemSummary(
            Long cartItemId,
            Long productId,
            String productName,
            String brandName,
            int basePrice,
            int quantity,
            int availableStock,
            String productStatus
    ) {}

    public record CartListResponse(
            List<CartItemSummary> items
    ) {}
}
