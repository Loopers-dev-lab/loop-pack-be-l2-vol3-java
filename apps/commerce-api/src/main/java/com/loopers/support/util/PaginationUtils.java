package com.loopers.support.util;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

public class PaginationUtils {

    private PaginationUtils() {
    }

    public static <T> Page<T> toPage(List<T> items, int page, int size) {
        int start = page * size;
        return new PageImpl<>(
                start >= items.size()
                        ? List.of()
                        : items.subList(start, Math.min(start + size, items.size())),
                PageRequest.of(page, size),
                items.size());
    }
}
