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
     *
     * <p>주문 유형, 상태, 총 금액, 만료 일시, 결제 일시 및 주문 항목 목록을 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminOrderResponse {
        private String orderId;
        private String userId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;
        private LocalDateTime paidAt;
        private List<AdminOrderItemResponse> items;

        /**
         * {@link OrderInfo}를 관리자 주문 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param info 주문 정보 DTO
         * @return 변환된 관리자 주문 응답 DTO
         */
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
     * 관리자용 주문 항목 응답 DTO.
     *
     * <p>주문 시점의 스냅샷 정보(상품명, 단가, 브랜드명)를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminOrderItemResponse {
        private String productId;
        private int quantity;
        private String snapshotProductName;
        private BigDecimal snapshotUnitPrice;
        private String snapshotBrandName;

        /**
         * {@link OrderInfo.OrderItemInfo}를 관리자 주문 항목 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param item 주문 항목 정보 DTO
         * @return 변환된 관리자 주문 항목 응답 DTO
         */
        public static AdminOrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return AdminOrderItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .snapshotProductName(item.getSnapshotProductName())
                    .snapshotUnitPrice(item.getSnapshotUnitPrice())
                    .snapshotBrandName(item.getSnapshotBrandName())
                    .build();
        }
    }
}
