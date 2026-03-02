package com.loopers.application.product;

import java.math.BigDecimal;

/**
 * 고객용 상품 목록 한 건 응답 DTO.
 * Product + Brand 이름 + 좋아요 수를 Application Layer에서 조합한 결과.
 * 목록 API에서 사용하며, ProductDetailInfo보다 필드를 최소화한다.
 */
public record ProductListItemInfo(
    Long id,
    String name,
    BigDecimal price,
    Long brandId,
    String brandName,
    long likeCount
) {
}
