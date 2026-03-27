package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderAdminSummaryResponse(
        Long id, int totalAmount, String status, int itemCount, ZonedDateTime createdAt
    ) {
        public static OrderAdminSummaryResponse from(OrderInfo info) {
            return new OrderAdminSummaryResponse(
                info.orderId(), info.totalAmount(), info.status(), info.items().size(), info.createdAt()
            );
        }
    }

    public record OrderAdminDetailResponse(
        Long id, Long memberId, int totalAmount, String status,
        List<OrderV1Dto.OrderItemResponse> orderItems, ZonedDateTime createdAt
    ) {
        public static OrderAdminDetailResponse from(OrderInfo info) {
            List<OrderV1Dto.OrderItemResponse> items = info.items().stream()
                .map(OrderV1Dto.OrderItemResponse::from)
                .toList();
            return new OrderAdminDetailResponse(
                info.orderId(), info.userId(), info.totalAmount(), info.status(), items, info.createdAt()
            );
        }
    }
}
