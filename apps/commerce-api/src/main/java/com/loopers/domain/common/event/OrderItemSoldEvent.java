package com.loopers.domain.common.event;

import java.util.Map;

/**
 * 상품 판매 이벤트 — "주문 확정으로 상품이 판매됐다"
 *
 * 발행 시점: TX2 커밋 이후 (AFTER_COMMIT)
 * 소비자: product_metrics 집계 (판매량 upsert)
 *
 * productQtyMap: productId → 판매 수량
 */
public record OrderItemSoldEvent(
        Long orderId,
        Map<Long, Integer> productQtyMap
) {
}
