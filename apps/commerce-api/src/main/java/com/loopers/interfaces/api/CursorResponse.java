package com.loopers.interfaces.api;

import java.util.List;
import java.util.function.Function;

public record CursorResponse<T>(
        List<T> content,
        Long nextCursor,
        boolean hasNext,
        int size
) {
    public static <T> CursorResponse<T> of(List<T> content, Long nextCursor, boolean hasNext, int size) {
        return new CursorResponse<>(content, nextCursor, hasNext, size);
    }

    public static <S, T> CursorResponse<T> of(List<S> content, Long nextCursor, boolean hasNext, int size, Function<S, T> mapper) {
        List<T> mapped = content.stream().map(mapper).toList();
        return new CursorResponse<>(mapped, nextCursor, hasNext, size);
    }
}
