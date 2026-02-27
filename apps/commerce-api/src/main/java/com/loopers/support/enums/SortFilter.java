package com.loopers.support.enums;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.loopers.infrastructure.product.entity.QProductEntity;

public enum SortFilter {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public OrderSpecifier<?> toOrderSpecifier(QProductEntity product,
                                               NumberExpression<Long> favoriteCnt) {
        return switch (this) {
            case LATEST -> product.id.desc();
            case PRICE_ASC -> product.price.asc();
            case LIKES_DESC -> favoriteCnt.desc();
        };
    }
}
