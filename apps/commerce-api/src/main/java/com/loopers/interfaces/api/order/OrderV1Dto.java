package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderResult;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(List<OrderItemRequest> items, Long couponIssueId) {

        public List<OrderItemCommand> toCommands() {
            return items.stream()
                .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                .toList();
        }
    }

    public record OrderItemRequest(Long productId, int quantity) {
    }

    public record OrderResponse(
        Long orderId,
        String status,
        int totalAmount,
        int discountAmount,
        int finalAmount,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.orderId(), info.status(), info.totalAmount(),
                info.discountAmount(), info.finalAmount(), items, info.createdAt()
            );
        }

        public static OrderResponse fromResult(OrderResult result) {
            List<OrderItemResponse> items = result.items().stream()
                .map(item -> new OrderItemResponse(
                    item.productId(), item.productName(),
                    item.productPrice().value(), item.quantity(), item.subtotal().value()
                ))
                .toList();
            return new OrderResponse(
                result.order().getId(), result.order().status().name(),
                result.order().totalAmount().value(),
                result.order().discountAmount().value(),
                result.order().finalAmount().value(),
                items, result.order().getCreatedAt()
            );
        }
    }

    public record OrderSummaryResponse(
        Long orderId,
        String status,
        int totalAmount,
        ZonedDateTime createdAt
    ) {

        public static OrderSummaryResponse from(OrderInfo info) {
            return new OrderSummaryResponse(
                info.orderId(), info.status(), info.totalAmount(), info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        int productPrice,
        int quantity,
        int subtotal
    ) {

        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(), item.productName(),
                item.productPrice(), item.quantity(), item.subtotal()
            );
        }
    }
}
