package com.loopers.application.product;

import java.math.BigDecimal;

/**
 * 고객용 상품 상세 응답 DTO.
 * Product + Brand 정보 + 좋아요 수를 Application Layer에서 조합한 결과.
 * interfaces DTO와 분리하며, ProductInfo(어드민/내부용)와 별도로 둔다.
 */
public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    BigDecimal price,
    int stockQuantity,
    long likeCount
) {
}
