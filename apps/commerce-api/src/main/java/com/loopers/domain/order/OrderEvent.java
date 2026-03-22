package com.loopers.domain.order;

import java.util.List;

/**
 * 주문 도메인에서 발생하는 이벤트.
 *
 * <p>Order 엔티티에서 {@code registerEvent()}로 등록되며,
 * {@code repository.save()} 시점에 발행된다.</p>
 */
public class OrderEvent {

    /**
     * 주문이 실패 처리되었을 때 발행되는 이벤트.
     *
     * <p>보상에 필요한 데이터를 포함하여, 리스너가 주문 도메인에 의존하지 않고
     * 자기 도메인의 보상 처리를 수행할 수 있도록 한다.</p>
     *
     * @param orderId 실패한 주문 ID
     * @param orderItems 주문 항목 스냅샷 (재고 복원용)
     * @param ownedCouponId 적용된 쿠폰 ID (nullable, 쿠폰 복원용)
     */
    public record OrderFailed(
            Long orderId,
            List<OrderItemSnapshot> orderItems,
            Long ownedCouponId
    ) {
    }

    /**
     * 주문 항목의 스냅샷.
     *
     * @param productId 상품 ID
     * @param quantity 수량
     */
    public record OrderItemSnapshot(Long productId, Long quantity) {
    }
}
