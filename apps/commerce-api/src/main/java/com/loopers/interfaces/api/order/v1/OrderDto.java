package com.loopers.interfaces.api.order.v1;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.order.Cart;
import com.loopers.application.order.Cart.CartItem;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.order.OrderStatus;

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

    public record OrderListResponse(
            Long orderId,
            String name,
            OrderStatus status,
            Long totalPrice,
            LocalDateTime orderedAt
    ) {

        public static OrderListResponse from(OrderResult result) {
            return new OrderListResponse(
                    result.id(),
                    result.name(),
                    result.status(),
                    result.totalPrice(),
                    result.orderedAt()
            );
        }
    }
}
