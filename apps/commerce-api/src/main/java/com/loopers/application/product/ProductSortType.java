package com.loopers.application.product;

import lombok.Getter;
import org.springframework.data.domain.Sort;

@Getter
public enum ProductSortType {

    LATEST("최신순"),
    PRICE_ASC("낮은 가격순"),
    LIKES_DESC("좋아요 많은순");

    private final String description;

    ProductSortType(String description) {
        this.description = description;
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "id");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
