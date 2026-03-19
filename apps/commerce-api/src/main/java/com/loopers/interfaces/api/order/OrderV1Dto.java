package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    /**
     * 주문 생성 요청
     */
    public record OrderCreateRequest(
            List<OrderItemRequest> items,
            Long userCouponId  // nullable. 쿠폰 미적용 시 null (BR-O09)
    ) {
        public OrderCreateCommand toCommand() {
            List<OrderCreateCommand.Item> commandItems = items.stream()
                    .map(item -> new OrderCreateCommand.Item(item.productId(), item.quantity()))
                    .toList();
            return new OrderCreateCommand(commandItems, userCouponId);
        }
    }

    public record OrderItemRequest(
            Long productId,
            int quantity
    ) {}

    /**
     * 주문 단건 응답 (생성/상세 조회 공용)
     */
    public record OrderResponse(
            Long orderId,
            Long userCouponId,
            String status,
            int originalAmount,
            int discountAmount,
            int finalAmount,
            ZonedDateTime createdAt,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userCouponId(),
                    info.status(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.finalAmount(),
                    info.createdAt(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
            Long orderItemId,
            Long productId,
            String productName,
            String brandName,
            int price,
            int quantity
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                    info.orderItemId(),
                    info.productId(),
                    info.productName(),
                    info.brandName(),
                    info.price(),
                    info.quantity()
            );
        }
    }

    /**
     * 주문 목록 응답
     */
    public record OrderListResponse(
            List<OrderResponse> orders
    ) {
        public static OrderListResponse from(List<OrderInfo> infos) {
            return new OrderListResponse(
                    infos.stream().map(OrderResponse::from).toList()
            );
        }
    }
}
