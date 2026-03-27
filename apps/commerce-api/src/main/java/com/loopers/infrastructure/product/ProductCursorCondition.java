package com.loopers.infrastructure.product;

import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.vo.Price;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.SimplePath;

import java.math.BigDecimal;

public enum ProductCursorCondition {

    LATEST {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            return p.updatedAt.lt(c.updatedAt())
                    .or(p.updatedAt.eq(c.updatedAt()).and(p.id.lt(c.id())));
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            return new OrderSpecifier<?>[] { p.updatedAt.desc(), p.id.desc() };
        }
    },

    LIKES_DESC {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            return LATEST.toCursorPredicate(p, c);
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            return LATEST.toOrderSpecifiers(p);
        }
    },

    PRICE_ASC {
        @Override
        public BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c) {
            SimplePath<Price> pricePath = Expressions.path(Price.class, p, "price");
            Price cursorPrice = new Price(c.price());
            BooleanExpression priceGreater = Expressions.booleanOperation(Ops.GT, pricePath, Expressions.constant(cursorPrice));
            BooleanExpression priceEqual = Expressions.booleanOperation(Ops.EQ, pricePath, Expressions.constant(cursorPrice));
            return priceGreater.or(priceEqual.and(p.id.gt(c.id())));
        }

        @Override
        public OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p) {
            NumberPath<BigDecimal> pricePath = Expressions.numberPath(BigDecimal.class, p, "price");
            return new OrderSpecifier<?>[] { pricePath.asc(), p.id.asc() };
        }
    };

    public abstract BooleanExpression toCursorPredicate(QProductModel p, ProductCursor c);

    public abstract OrderSpecifier<?>[] toOrderSpecifiers(QProductModel p);

    public static ProductCursorCondition from(String sortBy) {
        return switch (sortBy) {
            case "likes_desc" -> LIKES_DESC;
            case "price_asc"  -> PRICE_ASC;
            default           -> LATEST;
        };
    }

    public static ProductCursorCondition fromType(String cursorType) {
        return switch (cursorType) {
            case "LIKES_DESC" -> LIKES_DESC;
            case "PRICE_ASC"  -> PRICE_ASC;
            default           -> LATEST;
        };
    }
}