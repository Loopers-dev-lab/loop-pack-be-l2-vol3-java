package com.loopers.support.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;

import java.util.function.Supplier;

public class BooleanBuilderUtil {

    private BooleanBuilderUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static BooleanBuilder nullSafeBuilder(Supplier<BooleanExpression> f) {
        try {
            return new BooleanBuilder(f.get());
        } catch (final IllegalArgumentException | NullPointerException e) {
            return new BooleanBuilder();
        }
    }
}
