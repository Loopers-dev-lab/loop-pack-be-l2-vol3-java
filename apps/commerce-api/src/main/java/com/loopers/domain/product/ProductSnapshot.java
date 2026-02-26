package com.loopers.domain.product;

import java.math.BigDecimal;

/**
 * 주문 시점의 상품 스냅샷(이름·가격).
 * OrderItem 생성 시 사용한다.
 */
public record ProductSnapshot(
        Long productId,
        String productName,
        Money price) {
    /**
     * JPA/직렬화용 BigDecimal 가격. 스냅샷 생성 경로에서는 Money로 검증된 값이다.
     */
    public BigDecimal priceValue() {
        return price.value();
    }
}
