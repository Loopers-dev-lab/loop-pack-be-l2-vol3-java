package com.loopers.support.enums;

import com.querydsl.core.types.OrderSpecifier;
import com.loopers.infrastructure.product.entity.QProductEntity;

public enum SortFilter {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public OrderSpecifier<?>[] toOrderSpecifiers(QProductEntity product) {
        return switch (this) {
            case LATEST -> new OrderSpecifier<?>[]{ product.id.desc() };
            case PRICE_ASC -> new OrderSpecifier<?>[]{ product.price.asc(), product.id.desc() };
            case LIKES_DESC -> new OrderSpecifier<?>[]{ product.likeCount.desc(), product.id.desc() };
        };
    }
}
