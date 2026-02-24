package com.loopers.domain.product;

import java.math.BigDecimal;

/**
 * 주문 시점의 상품 스냅샷(이름·가격).
 * OrderItem 생성 시 사용한다.
 */
public record ProductSnapshot(
    Long productId,
    String productName,
    BigDecimal price
) {}
