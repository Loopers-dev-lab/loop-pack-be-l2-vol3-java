package com.loopers.domain.product;

import org.springframework.data.domain.Sort;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSortType {

    CREATED_AT_DESC(Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "price.amount").and(Sort.by(Sort.Direction.DESC, "createdAt"))),
    LIKE_COUNT_DESC(Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt")));

    public static final ProductSortType DEFAULT = CREATED_AT_DESC;

    private final Sort sort;

    public static ProductSortType from(String value) {
        if (value == null) {
            return DEFAULT;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.INVALID_SORT_TYPE);
        }
    }
}