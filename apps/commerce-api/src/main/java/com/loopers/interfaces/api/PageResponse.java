package com.loopers.interfaces.api;

import com.loopers.application.PageResult;
import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(
                result.items(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
