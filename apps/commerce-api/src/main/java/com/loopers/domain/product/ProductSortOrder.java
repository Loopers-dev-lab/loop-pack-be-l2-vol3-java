package com.loopers.domain.product;

import java.util.Locale;

/**
 * 상품 목록 정렬 기준.
 * 01 §3.7: 최신순(기본), 가격 낮은 순, 가격 높은 순, 인기순(좋아요 많은 순).
 */
public enum ProductSortOrder {
    LATEST,
    PRICE_ASC,
    PRICE_DESC,
    LIKES_DESC;

    /**
     * API sort 파라미터 문자열을 enum으로 변환한다.
     * null·빈 문자열·알 수 없는 값은 LATEST로 처리한다.
     */
    public static ProductSortOrder fromParam(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return LATEST;
        }
        return switch (sortParam.trim().toLowerCase(Locale.ROOT)) {
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }
}
