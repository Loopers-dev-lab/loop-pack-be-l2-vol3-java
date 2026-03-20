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
    private final Long orderId;
    private final Long userId;
    private final OrderType orderType;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final LocalDateTime expiresAt;
    private final LocalDateTime paidAt;
    private final List<OrderItemInfo> items;

    /**
     * OrderModel과 OrderItemModel 목록을 조합하여 OrderInfo DTO로 변환한다.
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
     */
    @Getter
    @Builder
    public static class OrderItemInfo {
        private final Long orderId;
        private final int orderItemSeq;
        private final Long productId;
        private final int quantity;
        private final String snapshotProductName;
        private final BigDecimal snapshotUnitPrice;
        private final String snapshotBrandId;
        private final String snapshotBrandName;
        private final String snapshotImageUrl;
        private final BigDecimal originalAmount;
        private final BigDecimal discountAmount;
        private final BigDecimal finalAmount;

        /**
         * OrderItemModel을 OrderItemInfo DTO로 변환한다.
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
                    .originalAmount(item.getOriginalAmount())
                    .discountAmount(item.getDiscountAmount())
                    .finalAmount(item.getFinalAmount())
                    .build();
        }
    }
}
