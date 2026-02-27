package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    /**
     * 관리자 주문 단건 응답 (상세 조회)
     */
    public record OrderResponse(
            Long orderId,
            Long userId,
            ZonedDateTime createdAt,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userId(),
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
     * 관리자 주문 목록 응답 (페이징)
     */
    public record OrderListResponse(
            List<OrderResponse> orders,
            long totalCount,
            int totalPages
    ) {
        public static OrderListResponse from(Page<OrderInfo> page) {
            return new OrderListResponse(
                    page.getContent().stream().map(OrderResponse::from).toList(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
