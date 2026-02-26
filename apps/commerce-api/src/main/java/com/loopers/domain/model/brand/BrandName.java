package com.loopers.domain.model.brand;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class BrandName {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 50;

    private final String value;

    private BrandName(String value) {
        this.value = value;
    }

    public static BrandName of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("브랜드 이름은 필수 입력값입니다.");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("브랜드 이름은 1~50자여야 합니다.");
        }
        return new BrandName(trimmed);
    }
}
