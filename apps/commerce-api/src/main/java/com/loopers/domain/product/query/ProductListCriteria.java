package com.loopers.domain.product.query;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public record ProductListCriteria(
        UUID brandId,
        UUID categoryId,
        Integer minPrice,
        Integer maxPrice,
        Boolean deleted,
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
        if (minPrice != null && minPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "minPrice는 0 이상이어야 합니다.");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "maxPrice는 0 이상이어야 합니다.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new CoreException(ErrorType.BAD_REQUEST, "minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }

    public static ProductListCriteria of(
            UUID brandId,
            UUID categoryId,
            Integer minPrice,
            Integer maxPrice,
            Boolean deleted,
            Integer page,
            Integer size,
            ProductSortOption sortOption
    ) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        ProductSortOption resolvedSortOption = sortOption == null ? ProductSortOption.defaultOption() : sortOption;
        return new ProductListCriteria(
                brandId,
                categoryId,
                minPrice,
                maxPrice,
                deleted,
                resolvedPage,
                resolvedSize,
                resolvedSortOption
        );
    }

    public static ProductListCriteria fromPublic(ProductListQuery query) {
        if (query.deleted() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제 상품 조회 조건은 관리자만 사용할 수 있습니다.");
        }

        ProductSortOption sortOption = ProductSortOption.fromApiValue(query.sort());
        return of(
                query.brandId(),
                query.categoryId(),
                query.minPrice(),
                query.maxPrice(),
                null,
                query.page(),
                query.size(),
                sortOption
        );
    }

    public static ProductListCriteria fromAdmin(ProductListQuery query) {
        ProductSortOption sortOption = ProductSortOption.fromApiValue(query.sort());
        return of(
                query.brandId(),
                query.categoryId(),
                query.minPrice(),
                query.maxPrice(),
                query.deleted(),
                query.page(),
                query.size(),
                sortOption
        );
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, sortOption.toSort());
    }
}
