package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
        Long couponId,
        @NotEmpty List<@Valid OrderItemRequest> items
    ) {}

    public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
    ) {
        public OrderFacade.OrderItemRequest toFacadeRequest() {
            return new OrderFacade.OrderItemRequest(productId, quantity);
        }
    }

    public record OrderResponse(Long orderId, Long userId, Long totalPrice,
                                Long discountAmount, Long finalPrice, List<OrderItemResponse> items) {

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(info.orderId(), info.userId(), info.totalPrice(),
                info.discountAmount(), info.finalPrice(), items);
        }
    }

    public record OrderItemResponse(Long productId, String productName, Long productPrice, Integer quantity) {

        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(),
                info.productName(),
                info.productPrice(),
                info.quantity()
            );
        }
    }
}
