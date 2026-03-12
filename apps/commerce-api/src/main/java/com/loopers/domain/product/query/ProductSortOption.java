package com.loopers.domain.product.query;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

import java.util.Locale;

public enum ProductSortOption {
    LATEST,
    LIKES,
    PRICE,
    NAME;

    public static ProductSortOption defaultOption() {
        return LATEST;
    }

    public static ProductSortOption fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return defaultOption();
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "latest" -> LATEST;
            case "likes" -> LIKES;
            case "price" -> PRICE;
            case "name" -> NAME;
            // @todo 구형 정렬 파라미터(price_asc, likes_desc) 사용처 제거 후 삭제
            case "price_asc" -> PRICE;
            case "likes_desc" -> LIKES;
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다: %s".formatted(value));
        };
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case LIKES -> Sort.by(Sort.Direction.DESC, "likeCount");
            case PRICE -> Sort.by(Sort.Direction.ASC, "price");
            case NAME -> Sort.by(Sort.Direction.ASC, "name");
        };
    }
}
