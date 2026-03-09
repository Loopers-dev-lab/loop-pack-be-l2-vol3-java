package com.loopers.interfaces.api.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 *
 * <p>주문 관련 REST API의 HTTP 요청 및 응답 데이터 구조를 정의한다.</p>
 */
public class OrderV1Dto {

    /**
     * 직접(DIRECT) 주문 생성 요청 DTO.
     *
     * <p>장바구니를 거치지 않고 직접 주문할 상품 항목 목록을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateDirectOrderRequest {
        @NotEmpty(message = "주문 항목은 필수입니다")
        @Valid
        private List<OrderItemDto> items;

        /**
         * 요청 DTO의 주문 항목을 도메인 서비스 파라미터 형식으로 변환한다.
         *
         * @return 변환된 {@link OrderItemRequest} 목록
         */
        public List<OrderItemCommand> toItems() {
            return items.stream()
                    .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                    .toList();
        }
    }

    /**
     * 장바구니(CART) 주문 생성 요청 DTO.
     *
     * <p>장바구니에 담긴 상품 중 주문할 항목 목록을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateCartOrderRequest {
        @NotEmpty(message = "주문 항목은 필수입니다")
        @Valid
        private List<OrderItemDto> items;

        /**
         * 요청 DTO의 주문 항목을 도메인 서비스 파라미터 형식으로 변환한다.
         *
         * @return 변환된 {@link OrderItemRequest} 목록
         */
        public List<OrderItemCommand> toItems() {
            return items.stream()
                    .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                    .toList();
        }
    }

    /**
     * 주문 항목 요청 DTO.
     *
     * <p>주문할 상품 ID와 수량을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemDto {
        @NotBlank(message = "상품 ID는 필수입니다")
        private String productId;
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private int quantity;
    }

    /**
     * 주문 목록 조회 응답 DTO.
     *
     * <p>주문 ID, 주문 유형, 상태, 총 금액, 만료 일시를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderResponse {
        private String orderId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;

        /**
         * {@link OrderInfo}를 주문 목록 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 주문 도메인 Info 객체
         * @return 변환된 OrderResponse
         */
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
     *
     * <p>주문 기본 정보와 함께 주문 항목 목록을 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderDetailResponse {
        private String orderId;
        private OrderType orderType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private LocalDateTime expiresAt;
        private List<OrderItemResponse> items;

        /**
         * {@link OrderInfo}를 주문 상세 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 주문 도메인 Info 객체
         * @return 변환된 OrderDetailResponse (주문 항목 포함)
         */
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
     * 주문 항목 응답 DTO.
     *
     * <p>주문 시점의 상품 스냅샷 정보(상품명, 단가, 브랜드명, 이미지 URL)를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class OrderItemResponse {
        private String productId;
        private int quantity;
        private String snapshotProductName;
        private BigDecimal snapshotUnitPrice;
        private String snapshotBrandName;
        private String snapshotImageUrl;

        /**
         * {@link OrderInfo.OrderItemInfo}를 주문 항목 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param item 변환할 주문 항목 도메인 Info 객체
         * @return 변환된 OrderItemResponse
         */
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return OrderItemResponse.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .snapshotProductName(item.getSnapshotProductName())
                    .snapshotUnitPrice(item.getSnapshotUnitPrice())
                    .snapshotBrandName(item.getSnapshotBrandName())
                    .snapshotImageUrl(item.getSnapshotImageUrl())
                    .build();
        }
    }
}
