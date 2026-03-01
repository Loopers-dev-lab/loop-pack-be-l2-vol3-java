package com.loopers.application.product;

import org.springframework.data.domain.Sort;

public enum ProductSortType {

    LATEST {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
    },
    PRICE_ASC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.ASC, "price");
        }
    },
    LIKES_DESC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.DESC, "likeCount");
        }
    };

    public abstract Sort toSort();

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }

        return switch (value.trim().toLowerCase()) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다: " + value);
        };
    }
}
