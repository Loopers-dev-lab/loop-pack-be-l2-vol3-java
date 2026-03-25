package com.loopers.domain.order;

import java.util.List;

/**
 * 주문 생성 이벤트.
 * OrderFacade에서 주문 저장 후 발행하며,
 * AFTER_COMMIT 시점에 OrderEventListener가 수신하여 로깅 등 부가 로직을 처리한다.
 */
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        int totalAmount,
        List<OrderItemSnapshot> items
) {
    public record OrderItemSnapshot(
            Long productId,
            String productName,
            int quantity,
            int price
    ) {}
}
