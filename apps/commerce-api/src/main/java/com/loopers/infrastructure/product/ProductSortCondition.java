package com.loopers.infrastructure.product;

import com.loopers.domain.product.QProductModel;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;

import java.math.BigDecimal;

public enum ProductSortCondition {

    PRICE_ASC {
        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel product) {
            NumberPath<BigDecimal> pricePath = Expressions.numberPath(BigDecimal.class, product, "price");
            return new OrderSpecifier<?>[] { pricePath.asc() };
        }
    },

    LATEST {
        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel product) {
            return new OrderSpecifier<?>[] { product.updatedAt.desc() };
        }
    };

    public abstract OrderSpecifier<?>[] toOrderSpecifiers(QProductModel product);

    public static ProductSortCondition from(String sortBy) {
        return switch (sortBy) {
            case "price_asc" -> PRICE_ASC;
            default          -> LATEST;
        };
    }
}
