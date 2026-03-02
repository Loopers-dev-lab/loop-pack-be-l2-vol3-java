package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
            List<OrderItemRequest> items
    ) {}

    public record OrderItemRequest(
            Long productId,
            Integer quantity
    ) {}

    public record Response(
            Long id,
            Long userId,
            BigDecimal totalAmount,
            List<OrderItemResponse> orderItems,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static Response from(OrderInfo info) {
            List<OrderItemResponse> items = info.orderItems().stream()
                    .map(OrderItemResponse::from)
                    .toList();

            return new Response(
                    info.id(),
                    info.userId(),
                    info.totalAmount(),
                    items,
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal totalPrice
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                    info.id(),
                    info.productId(),
                    info.productName(),
                    info.price(),
                    info.quantity(),
                    info.totalPrice()
            );
        }
    }

    public record PageResponse(
            List<Response> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static PageResponse from(org.springframework.data.domain.Page<OrderInfo> page) {
            List<Response> content = page.getContent().stream()
                    .map(Response::from)
                    .toList();

            return new PageResponse(
                    content,
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
