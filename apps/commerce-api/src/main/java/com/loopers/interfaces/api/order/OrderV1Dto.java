package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.domain.payment.CardType;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public class OrderV1Dto {

    public record CreateOrderRequest(
        List<OrderItemRequest> items,
        Long userCouponId,
        CardType cardType,
        String cardNo,
        boolean updateDefaultCard
    ) {}

    public record OrderItemRequest(
        Long productId,
        Long quantity
    ) {}

    public record CreateOrderResponse(
        Long orderId,
        String status,
        Long totalAmount,
        Long discountAmount,
        Long finalPrice,
        ZonedDateTime createdAt
    ) {
        public static CreateOrderResponse from(OrderInfo info) {
            return new CreateOrderResponse(
                info.orderId(),
                info.status().name(),
                info.totalAmount(),
                info.discountAmount(),
                info.finalPrice(),
                info.createdAt()
            );
        }
    }

    // 주문 목록 아이템 응답
    public record OrderSummaryResponse(
        Long orderId,
        String status,
        Long totalAmount,
        Long discountAmount,
        Long finalPrice,
        ZonedDateTime createdAt
    ) {
        public static OrderSummaryResponse from(OrderInfo info) {
            return new OrderSummaryResponse(
                info.orderId(),
                info.status().name(),
                info.totalAmount(),
                info.discountAmount(),
                info.finalPrice(),
                info.createdAt()
            );
        }
    }

    // 주문 아이템 응답 (상세 조회 내 포함)
    public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        String productName,
        String brandName,
        Long price,
        Long quantity,
        String status
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                info.orderItemId(),
                info.productId(),
                info.productName(),
                info.brandName(),
                info.price(),
                info.quantity(),
                info.status()
            );
        }
    }

    // 주문 상세 응답
    public record OrderDetailResponse(
        Long orderId,
        String status,
        Long totalAmount,
        ZonedDateTime createdAt,
        List<OrderItemResponse> items
    ) {
        public static OrderDetailResponse from(OrderDetailInfo info) {
            return new OrderDetailResponse(
                info.orderId(),
                info.status(),
                info.totalAmount(),
                info.createdAt(),
                info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    // 페이징 응답 래퍼
    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
