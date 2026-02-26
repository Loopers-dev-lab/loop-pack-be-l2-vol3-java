package com.loopers.domain.model.product;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProductName {

    private static final int MAX_LENGTH = 100;

    private final String value;

    private ProductName(String value) {
        this.value = value;
    }

    public static ProductName of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("상품 이름은 필수 입력값입니다.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("상품 이름은 100자 이하여야 합니다.");
        }
        return new ProductName(trimmed);
    }
}
