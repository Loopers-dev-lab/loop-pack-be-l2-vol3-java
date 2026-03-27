package com.loopers.domain.order;

import java.util.List;
import java.util.UUID;

/**
 * 주문 도메인에서 발생하는 이벤트.
 *
 * <p>Order 엔티티에서 {@code registerEvent()}로 등록되며,
 * {@code repository.save()} 시점에 발행된다.</p>
 */
public class OrderEvent {

    /**
     * 주문이 생성되었을 때 발행되는 이벤트.
     *
     * <p>재고 차감, 쿠폰 사용 처리 등 주문 생성에 따른 부수 효과를
     * 각 도메인 리스너가 {@code BEFORE_COMMIT} 단계에서 처리할 수 있도록 한다.</p>
     *
     * @param eventId       이벤트 식별자
     * @param orderId       주문 ID
     * @param orderItems    주문 항목 스냅샷 (재고 차감용)
     * @param ownedCouponId 적용된 쿠폰 ID (nullable, 쿠폰 사용 처리용)
     */
    public record OrderPlaced(
            UUID eventId,
            Long orderId,
            List<OrderItemSnapshot> orderItems,
            Long ownedCouponId
    ) {

        public static OrderPlaced from(Order order) {
            return new OrderPlaced(
                    UUID.randomUUID(),
                    order.getId(),
                    OrderItemSnapshot.from(order.getOrderItems()),
                    order.getOwnedCouponId()
            );
        }
    }

    /**
     * 주문이 실패 처리되었을 때 발행되는 이벤트.
     *
     * <p>보상에 필요한 데이터를 포함하여, 리스너가 주문 도메인에 의존하지 않고
     * 자기 도메인의 보상 처리를 수행할 수 있도록 한다.</p>
     *
     * @param eventId       이벤트 식별자
     * @param orderId       실패한 주문 ID
     * @param orderItems    주문 항목 스냅샷 (재고 복원용)
     * @param ownedCouponId 적용된 쿠폰 ID (nullable, 쿠폰 복원용)
     */
    public record OrderFailed(
            UUID eventId,
            Long orderId,
            List<OrderItemSnapshot> orderItems,
            Long ownedCouponId
    ) {

        public static OrderFailed from(Order order) {
            return new OrderFailed(
                    UUID.randomUUID(),
                    order.getId(),
                    OrderItemSnapshot.from(order.getOrderItems()),
                    order.getOwnedCouponId()
            );
        }
    }

    /**
     * 주문이 결제 완료되었을 때 발행되는 이벤트.
     *
     * <p>Outbox 이벤트가 함께 저장된 후, AFTER_COMMIT 시점에
     * 외부 발행을 트리거하기 위해 사용된다.
     * 외부 Consumer가 상품별 판매량을 집계할 수 있도록
     * 주문 항목 스냅샷을 포함한다.</p>
     *
     * @param eventId    이벤트 식별자
     * @param orderId    결제 완료된 주문 ID
     * @param orderItems 주문 항목 스냅샷 (상품별 판매량 집계용)
     */
    public record OrderCompleted(UUID eventId, Long orderId, List<OrderItemSnapshot> orderItems) {

        public static OrderCompleted from(Order order) {
            return new OrderCompleted(
                    UUID.randomUUID(),
                    order.getId(),
                    OrderItemSnapshot.from(order.getOrderItems())
            );
        }
    }

    /**
     * 주문 항목의 스냅샷.
     *
     * @param productId 상품 ID
     * @param quantity  수량
     */
    public record OrderItemSnapshot(Long productId, Long quantity) {

        public static List<OrderItemSnapshot> from(List<OrderItem> orderItems) {
            return orderItems.stream()
                    .map(item -> new OrderItemSnapshot(item.getProductId(), item.getQuantity()))
                    .toList();
        }
    }
}
