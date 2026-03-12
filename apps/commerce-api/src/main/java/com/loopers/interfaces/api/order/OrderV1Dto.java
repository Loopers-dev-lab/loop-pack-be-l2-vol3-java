package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(List<OrderItemRequest> items) {

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
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {

        public static OrderResponse from(OrderResult result) {
            List<OrderItemResponse> items = result.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                result.order().getId(),
                result.order().status().name(),
                result.order().totalAmount().value(),
                items,
                result.order().getCreatedAt()
            );
        }

        public static OrderResponse from(OrderModel order, List<OrderItemModel> items) {
            List<OrderItemResponse> itemResponses = items.stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                order.getId(),
                order.status().name(),
                order.totalAmount().value(),
                itemResponses,
                order.getCreatedAt()
            );
        }
    }

    public record OrderSummaryResponse(
        Long orderId,
        String status,
        int totalAmount,
        ZonedDateTime createdAt
    ) {

        public static OrderSummaryResponse from(OrderModel order) {
            return new OrderSummaryResponse(
                order.getId(),
                order.status().name(),
                order.totalAmount().value(),
                order.getCreatedAt()
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

        public static OrderItemResponse from(OrderItemModel item) {
            return new OrderItemResponse(
                item.productId(),
                item.productName(),
                item.productPrice().value(),
                item.quantity(),
                item.subtotal().value()
            );
        }
    }
}
