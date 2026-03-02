package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderSummaryInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

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
