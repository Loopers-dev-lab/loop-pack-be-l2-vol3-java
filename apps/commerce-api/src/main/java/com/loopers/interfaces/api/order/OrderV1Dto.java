package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrderV1Dto {

    public record CreateOrderRequest(

        @NotEmpty(message = "주문 상품은 필수입니다")
        @Valid
        List<OrderItemRequest> items
    ) {
        public Map<Long, Integer> toProductQuantities() {
            return items.stream()
                .collect(Collectors.toMap(
                    OrderItemRequest::productId,
                    OrderItemRequest::quantity
                ));
        }
    }

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        Integer quantity
    ) {

    }

    public record CreateOrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        Integer totalPrice,
        ZonedDateTime orderDt
    ) {

        public static CreateOrderResponse from(OrderInfo orderInfo) {
            return new CreateOrderResponse(
                orderInfo.id(),
                orderInfo.userId(),
                orderInfo.status(),
                orderInfo.totalPrice(),
                orderInfo.orderDt()
            );
        }
    }
}
