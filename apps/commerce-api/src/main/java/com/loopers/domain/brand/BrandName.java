package com.loopers.domain.brand;

/**
 * Brand 이름 VO
 */
public record BrandName(String value) {

    private static final int MAX_LENGTH = 100;

    public BrandName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("브랜드명은 필수입니다");
        }

        value = value.trim();

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("브랜드명은 100자 이하여야 합니다");
        }
    }
}
