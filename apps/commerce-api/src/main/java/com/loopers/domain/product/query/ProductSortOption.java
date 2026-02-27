package com.loopers.domain.product.query;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

public enum ProductSortOption {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static ProductSortOption defaultOption() {
        return LATEST;
    }

    public static ProductSortOption fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return defaultOption();
        }

        return switch (value) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다: %s".formatted(value));
        };
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
