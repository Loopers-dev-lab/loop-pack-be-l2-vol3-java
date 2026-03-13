package com.loopers.application;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                items.stream().map(mapper).toList(),
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
