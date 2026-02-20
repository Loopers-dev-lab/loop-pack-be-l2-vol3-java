package com.loopers.domain.brand;

public class BrandFixture {

    public static final String DEFAULT_NAME = "테스트 브랜드";
    public static final String DEFAULT_LOGO_URL = "https://example.com/logo.png";
    public static final String DEFAULT_DESCRIPTION = "브랜드 설명";

    public static Brand createBrand() {
        return Brand.create(DEFAULT_NAME, DEFAULT_LOGO_URL, DEFAULT_DESCRIPTION);
    }
}