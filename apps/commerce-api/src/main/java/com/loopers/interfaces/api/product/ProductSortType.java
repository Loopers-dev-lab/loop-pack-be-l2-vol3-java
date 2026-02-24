package com.loopers.interfaces.api.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

// 고객용 상품 목록 조회 시 지원하는 정렬 기준
public enum ProductSortType {

    LATEST,     // 최신순 (기본값)
    PRICE_ASC,  // 가격 오름차순
    LIKES_DESC; // 좋아요 많은 순

    public static ProductSortType from(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "지원하지 않는 정렬 기준입니다. 허용값: latest, price_asc, likes_desc");
        }
    }

    public Sort toSort() {
        return switch (this) {
            case PRICE_ASC -> Sort.by("price.amount").ascending();
            case LIKES_DESC -> Sort.by("likeCount").descending();
            case LATEST -> Sort.by("createdAt").descending();
        };
    }
}
