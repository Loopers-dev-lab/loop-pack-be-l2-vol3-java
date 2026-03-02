package com.loopers.domain.product;

/**
 * 재고 수량 값 객체.
 * 0 이상만 허용한다.
 */
public record StockQuantity(int value) {

    public StockQuantity {
        if (value < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
    }

    public static StockQuantity of(int value) {
        return new StockQuantity(value);
    }
}
