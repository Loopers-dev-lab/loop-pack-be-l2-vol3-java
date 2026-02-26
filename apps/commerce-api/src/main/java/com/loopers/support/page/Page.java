package com.loopers.support.page;

import java.util.List;
import java.util.function.Function;

public record Page<T>(
        List<T> content,
        boolean hasNext
) {

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(
                content.stream().map(mapper).toList(),
                hasNext
        );
    }
}
