package com.loopers.interfaces.api.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PaginationQuery(Integer page, Integer size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PaginationQuery {
        if (page != null && page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size != null && size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        if (size != null && size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 " + MAX_SIZE + "을(를) 초과할 수 없습니다.");
        }
    }

    public int resolvedPage() {
        return page != null ? page : DEFAULT_PAGE;
    }

    public int resolvedSize() {
        return size != null ? size : DEFAULT_SIZE;
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(resolvedPage(), resolvedSize(), sort == null ? Sort.unsorted() : sort);
    }
}
