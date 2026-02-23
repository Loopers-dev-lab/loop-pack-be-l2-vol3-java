package com.loopers.interfaces.api.order.v1;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.order.Cart;
import com.loopers.application.order.Cart.CartItem;

public class OrderDto {

    public record CreateOrderRequest(
            @NotEmpty(message = "주문 항목은 필수입니다.") List<OrderItemRequest> orderItems
    ) {

        public Cart toCart(Long userId) {
            List<CartItem> items = orderItems.stream()
                    .map(OrderItemRequest::toCartItem)
                    .toList();
            return new Cart(userId, items);
        }
    }

    public record OrderItemRequest(
            @NotNull(message = "상품 ID는 필수입니다.") Long productId,
            @NotNull(message = "수량은 필수입니다.") Long quantity
    ) {

        public CartItem toCartItem() {
            return new CartItem(productId, quantity);
        }
    }

    public record CreateOrderResponse(Long orderId) {

        public static CreateOrderResponse from(Long orderId) {
            return new CreateOrderResponse(orderId);
        }
    }
}
