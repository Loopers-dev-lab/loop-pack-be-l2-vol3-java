package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,
        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity,
        Long optionId
    ) {
    }

    public record CreateOrderRequest(
        @NotNull(message = "주문 항목은 필수입니다.")
        @Valid
        List<OrderItemRequest> items
    ) {
    }

    public record OrderItemResponse(
        Long productId,
        String productNameSnapshot,
        BigDecimal priceSnapshot,
        int quantity,
        Long optionId
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            if (info == null) {
                return null;
            }
            return new OrderItemResponse(
                info.productId(),
                info.productNameSnapshot(),
                info.priceSnapshot(),
                info.quantity(),
                info.optionId()
            );
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        ZonedDateTime orderedAt,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            if (info == null) {
                return null;
            }
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status(),
                info.orderedAt(),
                items
            );
        }
    }
}
