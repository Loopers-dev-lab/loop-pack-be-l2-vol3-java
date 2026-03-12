package com.loopers.application.order;

import java.util.List;

/**
 * 할인이 적용된 주문 생성 요청.
 */
public record PlaceOrderWithDiscountRequest(
    List<OrderItemRequest> items,
    Long couponId,
    Long pointAmount
) {
    public boolean hasCoupon() {
        return couponId != null;
    }

    public boolean hasPoint() {
        return pointAmount != null && pointAmount > 0;
    }
}
