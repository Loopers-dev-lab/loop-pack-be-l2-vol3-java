package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 정보 DTO.
 * <p>
 * 주문 엔티티와 주문 항목 엔티티 목록을 조합하여
 * 도메인 모델을 직접 노출하지 않고 interfaces 계층에 전달하기 위한 응답 객체이다.
 * 주문 항목에는 주문 시점의 상품 스냅샷 정보가 포함된다.
 * </p>
 */
@Getter
@Builder
public class OrderInfo {
    private final String orderId;
    private final String userId;
    private final OrderType orderType;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final LocalDateTime expiresAt;
    private final LocalDateTime paidAt;
    private final List<OrderItemInfo> items;

    /**
     * OrderModel과 OrderItemModel 목록을 조합하여 OrderInfo DTO로 변환한다.
     *
     * @param order 주문 엔티티
     * @param items 주문 항목 엔티티 목록 (null이면 빈 리스트)
     * @return 주문 정보 DTO (주문 항목 스냅샷 포함)
     */
    public static OrderInfo from(OrderModel order, List<OrderItemModel> items) {
        return OrderInfo.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .items(items != null
                        ? items.stream().map(OrderItemInfo::from).toList()
                        : List.of())
                .build();
    }

    /**
     * 주문 항목 정보 DTO.
     * <p>
     * 주문 시점의 상품 스냅샷 데이터(상품명, 단가, 브랜드, 이미지 등)를 포함하여
     * 주문 후 상품 정보가 변경되더라도 주문 당시 정보를 보존한다.
     * </p>
     */
    @Getter
    @Builder
    public static class OrderItemInfo {
        private final String orderId;
        private final int orderItemSeq;
        private final String productId;
        private final int quantity;
        private final String snapshotProductName;
        private final BigDecimal snapshotUnitPrice;
        private final String snapshotBrandId;
        private final String snapshotBrandName;
        private final String snapshotImageUrl;

        /**
         * OrderItemModel을 OrderItemInfo DTO로 변환한다.
         *
         * @param item 주문 항목 엔티티
         * @return 주문 항목 정보 DTO (스냅샷 데이터 포함)
         */
        public static OrderItemInfo from(OrderItemModel item) {
            return OrderItemInfo.builder()
                    .orderId(item.getOrderId())
                    .orderItemSeq(item.getOrderItemSeq())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .snapshotProductName(item.getSnapshotProductName())
                    .snapshotUnitPrice(item.getSnapshotUnitPrice())
                    .snapshotBrandId(item.getSnapshotBrandId())
                    .snapshotBrandName(item.getSnapshotBrandName())
                    .snapshotImageUrl(item.getSnapshotImageUrl())
                    .build();
        }
    }
}
