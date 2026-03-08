package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDto;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다")
        @Valid
        List<OrderItemRequest> items,

        Long couponId
    ) {
    }

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @NotNull(message = "주문 수량은 필수입니다")
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다")
        Integer quantity
    ) {
        public OrderDto.OrderLineCommand toCommand() {
            return new OrderDto.OrderLineCommand(productId, quantity);
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        BigDecimal productPrice,
        Integer quantity,
        BigDecimal subtotal
    ) {
        public static OrderItemResponse from(OrderDto.OrderItemInfo itemInfo) {
            return new OrderItemResponse(
                itemInfo.productId(),
                itemInfo.productName(),
                itemInfo.productPrice(),
                itemInfo.quantity(),
                itemInfo.subtotal()
            );
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        Long couponId,
        BigDecimal discountAmount,
        OrderStatus status,
        BigDecimal totalAmount,
        ZonedDateTime createdAt,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderDto.OrderInfo orderInfo) {
            return new OrderResponse(
                orderInfo.id(),
                orderInfo.userId(),
                orderInfo.couponId(),
                orderInfo.discountAmount(),
                orderInfo.status(),
                orderInfo.totalAmount(),
                orderInfo.createdAt(),
                orderInfo.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static OrderPageResponse from(Page<OrderDto.OrderInfo> page) {
            return new OrderPageResponse(
                page.getContent().stream().map(OrderResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
