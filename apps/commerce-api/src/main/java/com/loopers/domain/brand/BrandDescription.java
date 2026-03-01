package com.loopers.domain.brand;

/**
 * Brand 설명 VO
 */
public record BrandDescription(String value) {

    private static final int MAX_LENGTH = 2000;

    public BrandDescription {
        if (value == null) {
            value = "";
        } else {
            value = value.trim();
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("브랜드 설명은 2000자 이하여야 합니다");
        }
    }
}
