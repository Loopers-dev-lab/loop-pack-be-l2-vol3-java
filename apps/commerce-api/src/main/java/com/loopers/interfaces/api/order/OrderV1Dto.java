package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderSummaryInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record PlaceOrderRequest(
        @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
        List<@Valid PlaceOrderItemRequest> items
    ) {}

    public record PlaceOrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
        int quantity
    ) {}

    public record OrderSummaryResponse(
        Long id,
        Long userId,
        Long totalAmount,
        ZonedDateTime orderedAt
    ) {
        public static OrderSummaryResponse from(OrderSummaryInfo info) {
            return new OrderSummaryResponse(
                info.id(),
                info.userId(),
                info.totalAmount(),
                info.orderedAt()
            );
        }
    }

    public record OrderDetailResponse(
        Long id,
        Long userId,
        Long totalAmount,
        ZonedDateTime orderedAt,
        List<OrderItemResponse> items
    ) {
        public static OrderDetailResponse from(OrderDetailInfo info) {
            return new OrderDetailResponse(
                info.id(),
                info.userId(),
                info.totalAmount(),
                info.orderedAt(),
                info.items().stream()
                    .map(OrderItemResponse::from)
                    .toList()
            );
        }
    }

    public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Long unitPrice,
        int quantity,
        Long lineTotalAmount
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.id(),
                info.productId(),
                info.productName(),
                info.unitPrice(),
                info.quantity(),
                info.lineTotalAmount()
            );
        }
    }
}
