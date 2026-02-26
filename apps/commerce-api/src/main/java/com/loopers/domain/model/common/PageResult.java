package com.loopers.domain.model.common;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> content, int page, int size,
        long totalElements, int totalPages
) {
    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mapped = content.stream().map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages);
    }
}
