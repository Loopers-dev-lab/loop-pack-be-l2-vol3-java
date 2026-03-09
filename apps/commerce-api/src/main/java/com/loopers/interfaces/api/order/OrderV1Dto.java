package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderHistoryInfo;
import com.loopers.application.order.OrderInfo;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
            List<OrderItemRequest> items,
            Long userCouponId
    ) {}

    public record OrderItemRequest(
            Long productId,
            Integer quantity
    ) {}

    public record Response(
            Long id,
            Long userId,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            Long userCouponId,
            String status,
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
                    info.originalAmount(),
                    info.discountAmount(),
                    info.totalAmount(),
                    info.userCouponId(),
                    info.status(),
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

    public record HistoryResponse(
            Long id,
            Long orderId,
            String previousStatus,
            String newStatus,
            String description,
            ZonedDateTime createdAt
    ) {
        public static HistoryResponse from(OrderHistoryInfo info) {
            return new HistoryResponse(
                    info.id(),
                    info.orderId(),
                    info.previousStatus(),
                    info.newStatus(),
                    info.description(),
                    info.createdAt()
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
