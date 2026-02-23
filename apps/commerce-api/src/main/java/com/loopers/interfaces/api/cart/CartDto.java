package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartInfo;
import com.loopers.domain.cart.CartItem;

import java.math.BigDecimal;
import java.util.List;

public class CartDto {

    public record AddRequest(Long optionId, int quantity) {}

    public record UpdateQuantityRequest(int quantity) {}

    public record CartItemResponse(
            Long cartItemId,
            Long productId,
            String productName,
            Long optionId,
            String optionName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal totalPrice,
            boolean orderable
    ) {
        public static CartItemResponse from(CartInfo.CartItemInfo info) {
            return new CartItemResponse(
                    info.getCartItemId(),
                    info.getProductId(),
                    info.getProductName(),
                    info.getOptionId(),
                    info.getOptionName(),
                    info.getUnitPrice().getAmount(),
                    info.getQuantity(),
                    info.getTotalPrice().getAmount(),
                    info.isOrderable()
            );
        }
    }

    public record CartResponse(
            List<CartItemResponse> items,
            BigDecimal totalAmount
    ) {
        public static CartResponse from(CartInfo info) {
            return new CartResponse(
                    info.getItems().stream().map(CartItemResponse::from).toList(),
                    info.getTotalAmount().getAmount()
            );
        }
    }

    public record AddResponse(Long cartItemId, Long optionId, int quantity) {
        public static AddResponse from(CartItem cartItem) {
            return new AddResponse(
                    cartItem.getId(),
                    cartItem.getOptionId(),
                    cartItem.getQuantity()
            );
        }
    }
}
