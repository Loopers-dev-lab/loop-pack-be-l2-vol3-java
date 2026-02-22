package com.loopers.domain.product;

public enum ProductSortType {
    CREATED_AT_DESC,
    PRICE_ASC,
    LIKE_COUNT_DESC;

    public static final ProductSortType DEFAULT = CREATED_AT_DESC;
}
