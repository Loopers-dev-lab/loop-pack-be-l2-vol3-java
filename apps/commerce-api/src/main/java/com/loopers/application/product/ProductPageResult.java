package com.loopers.application.product;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 상품 목록 조회 결과를 캐시에 저장하기 위한 직렬화 가능 record.
 * Page<ProductInfo>는 PageImpl에 @JsonCreator가 없어 Jackson 역직렬화 불가 → 직접 필드를 선언하여 해결
 */
public record ProductPageResult(
        List<ProductInfo> products,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ProductPageResult from(Page<ProductInfo> page) {
        return new ProductPageResult(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
