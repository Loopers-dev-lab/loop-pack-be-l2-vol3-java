package com.loopers.interfaces.apiadmin;

import com.loopers.application.order.OrderInfo;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 주문 API의 요청/응답 DTO를 정의하는 클래스.
 */
public class AdminOrderV1Dto {

    /**
     * 관리자용 주문 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminOrderResponse {
        private Long orderId;
        private Long userId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;
        private LocalDateTime paidAt;
        private List<AdminOrderItemResponse> items;

        public static AdminOrderResponse from(OrderInfo info) {
            return AdminOrderResponse.builder()
                    .orderId(info.getOrderId())
                    .userId(info.getUserId())
                    .orderType(info.getOrderType())
                    .status(info.getStatus())
                    .totalAmount(info.getTotalAmount())
                    .expiresAt(info.getExpiresAt())
                    .paidAt(info.getPaidAt())
                    .items(info.getItems() != null
                            ? info.getItems().stream().map(AdminOrderItemResponse::from).toList()
                            : List.of())
                    .build();
        }
    }

    /**
     * 관리자용 주문 항목 응답 DTO (할인 금액 필드 포함).
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminOrderItemResponse {
        private Long productId;
        private int quantity;
        private String snapshotProductName;
        private BigDecimal snapshotUnitPrice;
        private String snapshotBrandName;
        private BigDecimal originalAmount;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;

        public static AdminOrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return AdminOrderItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .snapshotProductName(item.getSnapshotProductName())
                    .snapshotUnitPrice(item.getSnapshotUnitPrice())
                    .snapshotBrandName(item.getSnapshotBrandName())
                    .originalAmount(item.getOriginalAmount())
                    .discountAmount(item.getDiscountAmount())
                    .finalAmount(item.getFinalAmount())
                    .build();
        }
    }
}
