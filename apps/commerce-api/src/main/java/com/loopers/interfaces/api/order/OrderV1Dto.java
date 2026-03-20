package com.loopers.interfaces.api.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 API V1 요청/응답 DTO 모음.
 */
public class OrderV1Dto {

    /**
     * 직접(DIRECT) 주문 생성 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateDirectOrderRequest {
        @NotEmpty(message = "주문 항목은 필수입니다")
        @Valid
        private List<OrderItemDto> items;

        private Long couponId;  // 발급된 쿠폰 ID (user_coupon_id), nullable

        public List<OrderItemCommand> toItems() {
            return items.stream()
                    .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                    .toList();
        }
    }

    /**
     * 장바구니(CART) 주문 생성 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateCartOrderRequest {
        @NotEmpty(message = "주문 항목은 필수입니다")
        @Valid
        private List<OrderItemDto> items;

        private Long couponId;  // 발급된 쿠폰 ID (user_coupon_id), nullable

        public List<OrderItemCommand> toItems() {
            return items.stream()
                    .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                    .toList();
        }
    }

    /**
     * 주문 항목 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemDto {
        private Long productId;
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private int quantity;
    }

    /**
     * 주문 목록 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderResponse {
        private Long orderId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;

        public static OrderResponse from(OrderInfo info) {
            return OrderResponse.builder()
                    .orderId(info.getOrderId())
                    .orderType(info.getOrderType())
                    .status(info.getStatus())
                    .totalAmount(info.getTotalAmount())
                    .expiresAt(info.getExpiresAt())
                    .build();
        }
    }

    /**
     * 주문 상세 조회 응답 DTO.
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderDetailResponse {
        private Long orderId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;
        private List<OrderItemResponse> items;

        public static OrderDetailResponse from(OrderInfo info) {
            return OrderDetailResponse.builder()
                    .orderId(info.getOrderId())
                    .orderType(info.getOrderType())
                    .status(info.getStatus())
                    .totalAmount(info.getTotalAmount())
                    .expiresAt(info.getExpiresAt())
                    .items(info.getItems() != null
                            ? info.getItems().stream().map(OrderItemResponse::from).toList()
                            : List.of())
                    .build();
        }
    }

    /**
     * 주문 항목 응답 DTO (할인 금액 필드 포함).
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderItemResponse {
        private Long productId;
        private int quantity;
        private String snapshotProductName;
        private BigDecimal snapshotUnitPrice;
        private String snapshotBrandName;
        private String snapshotImageUrl;
        private BigDecimal originalAmount;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;

        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return OrderItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .snapshotProductName(item.getSnapshotProductName())
                    .snapshotUnitPrice(item.getSnapshotUnitPrice())
                    .snapshotBrandName(item.getSnapshotBrandName())
                    .snapshotImageUrl(item.getSnapshotImageUrl())
                    .originalAmount(item.getOriginalAmount())
                    .discountAmount(item.getDiscountAmount())
                    .finalAmount(item.getFinalAmount())
                    .build();
        }
    }
}
