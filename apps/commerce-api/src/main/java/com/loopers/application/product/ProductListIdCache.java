package com.loopers.application.product;

import java.io.Serializable;
import java.util.List;

/**
 * 상품 목록 캐시용 경량 DTO.
 * <p>
 * 캐시 위계 분리를 위해 상품 ID 목록과 페이징 메타데이터만 저장한다.
 * 개별 상품 정보는 productDetail 캐시에서 별도 조회한다.
 * </p>
 */
public record ProductListIdCache(
        List<Long> productIds,
        int page,
        int size,
        long totalElements,
        int totalPages
) implements Serializable {
}
