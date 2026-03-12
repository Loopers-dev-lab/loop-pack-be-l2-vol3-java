package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderItemRequest;
import com.loopers.application.order.OrderItemResult;
import com.loopers.application.order.OrderResult;
import com.loopers.application.order.PlaceOrderWithDiscountRequest;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 API DTO.
 */
public class OrderV1Dto {

    private OrderV1Dto() {}

    /**
     * 주문 생성 요청.
     */
    public record PlaceOrderRequestDto(
        List<OrderItemRequestDto> items
    ) {
        public List<OrderItemRequest> toServiceRequest() {
            return items.stream()
                .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
                .toList();
        }
    }

    /**
     * 주문 항목 요청.
     */
    public record OrderItemRequestDto(
        Long productId,
        int quantity
    ) {}

    /**
     * 할인 적용 주문 생성 요청.
     */
    public record PlaceOrderWithDiscountRequestDto(
        List<OrderItemRequestDto> items,
        Long couponId,
        Long pointAmount
    ) {
        public PlaceOrderWithDiscountRequest toServiceRequest() {
            List<OrderItemRequest> itemRequests = items.stream()
                .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
                .toList();
            return new PlaceOrderWithDiscountRequest(itemRequests, couponId, pointAmount);
        }
    }

    /**
     * 주문 응답.
     */
    public record OrderResponseDto(
        Long id,
        Long userId,
        List<OrderItemResponseDto> items,
        Long totalPrice,
        Long originalAmount,
        Long couponDiscount,
        Long pointDiscount,
        Long couponId,
        OrderStatus status,
        ZonedDateTime createdAt
    ) {
        public static OrderResponseDto from(OrderResult result) {
            List<OrderItemResponseDto> items = result.items().stream()
                .map(OrderItemResponseDto::from)
                .toList();

            return new OrderResponseDto(
                result.id(),
                result.userId(),
                items,
                result.totalPrice(),
                result.originalAmount(),
                result.couponDiscount(),
                result.pointDiscount(),
                result.couponId(),
                result.status(),
                result.createdAt()
            );
        }
    }

    /**
     * 주문 항목 응답.
     */
    public record OrderItemResponseDto(
        Long id,
        Long productId,
        String productName,
        int quantity,
        Long priceSnapshot
    ) {
        public static OrderItemResponseDto from(OrderItemResult result) {
            return new OrderItemResponseDto(
                result.id(),
                result.productId(),
                result.productName(),
                result.quantity(),
                result.priceSnapshot()
            );
        }
    }

    /**
     * 주문 목록 응답.
     */
    public record OrderListResponseDto(
        List<OrderResponseDto> orders,
        long totalCount
    ) {}
}
