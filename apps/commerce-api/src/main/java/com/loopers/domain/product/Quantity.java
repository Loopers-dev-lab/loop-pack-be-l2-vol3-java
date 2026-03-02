package com.loopers.domain.product;

import java.math.BigDecimal;

/**
 * 주문·장바구니·재고 복구 등에서 사용하는 수량 값 객체.
 * 1 이상만 허용한다.
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }
}
