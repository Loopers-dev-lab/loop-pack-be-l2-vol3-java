package com.loopers.domain.product.query;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public record ProductListCriteria(
        UUID brandId,
        int page,
        int size,
        ProductSortOption sortOption
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public ProductListCriteria {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        if (size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 " + MAX_SIZE + "을(를) 초과할 수 없습니다.");
        }
        if (sortOption == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정렬 옵션은 필수입니다.");
        }
    }

    public static ProductListCriteria of(UUID brandId, Integer page, Integer size, ProductSortOption sortOption) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        ProductSortOption resolvedSortOption = sortOption == null ? ProductSortOption.defaultOption() : sortOption;
        return new ProductListCriteria(brandId, resolvedPage, resolvedSize, resolvedSortOption);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, sortOption.toSort());
    }
}
