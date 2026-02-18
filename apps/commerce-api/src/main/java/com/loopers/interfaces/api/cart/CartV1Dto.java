package com.loopers.interfaces.api.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CartV1Dto {

    public record AddRequest(
        @NotNull(message = "상품 ID는 필수입니다.") Long productId,
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int quantity
    ) {}

    public record UpdateQuantityRequest(
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int quantity
    ) {}

    public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        String brandName,
        int price,
        int quantity
    ) {
        public static CartItemResponse from(CartItem cartItem, Product product, Brand brand) {
            return new CartItemResponse(
                cartItem.getId(), product.getId(), product.getName(),
                brand.getName(), product.getPrice().amount(), cartItem.getQuantity().value()
            );
        }
    }

    public record CartResponse(List<CartItemResponse> items) {
        public static CartResponse from(List<CartItemResponse> items) {
            return new CartResponse(items);
        }
    }
}
