package com.loopers.interfaces.api.common;

import com.loopers.domain.model.common.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T, R> PageResponse<R> from(PageResult<T> pageResult, Function<T, R> mapper) {
        List<R> content = pageResult.content().stream()
                .map(mapper)
                .toList();
        return new PageResponse<>(content, pageResult.page(), pageResult.size(),
                pageResult.totalElements(), pageResult.totalPages());
    }
}
