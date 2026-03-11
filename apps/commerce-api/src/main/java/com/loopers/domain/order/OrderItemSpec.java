package com.loopers.domain.order;

import com.loopers.domain.common.Money;

/**
 * 주문 시 사용되는 주문 상품 스펙 값 객체
 * - 애그리거트 외부(Application 계층 등)에서 Order 생성 시 사용
 */
public record OrderItemSpec(
    Long productId,
    Money price,
    int quantity
) {
}
