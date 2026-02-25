package com.loopers.interfaces.api.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

public enum ProductSortType {
    LATEST("latest", Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC("price_asc", Sort.by(Sort.Direction.ASC, "price")),
    LIKES_DESC("likes_desc", Sort.by(Sort.Direction.DESC, "likeCount"));

    private final String value;
    private final Sort sort;

    ProductSortType(String value, Sort sort) {
        this.value = value;
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }

        for (ProductSortType sortType : values()) {
            if (sortType.value.equals(value)) {
                return sortType;
            }
        }

        throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다: %s".formatted(value));
    }
}
