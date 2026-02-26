package com.loopers.domain.model.product;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Price {

    private final int value;

    private Price(int value) {
        this.value = value;
    }

    public static Price of(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        }
        return new Price(value);
    }
}
