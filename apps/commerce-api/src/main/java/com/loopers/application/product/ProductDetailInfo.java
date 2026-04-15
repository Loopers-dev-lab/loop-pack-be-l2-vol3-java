package com.loopers.application.product;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * 고객용 상품 상세 응답 DTO.
 * Product + Brand 정보 + 좋아요 수를 Application Layer에서 조합한 결과.
 * interfaces DTO와 분리하며, ProductInfo(어드민/내부용)와 별도로 둔다.
 * <p>
 * {@code rankingRank}는 Redis 일간 ZSET 기준이며, 캐시에는 넣지 않고 응답 직전에만 채운다.
 * 랭킹 목록 API와는 요청 시점이 달라 순위가 미세히 어긋날 수 있다(design §4.2.6, §13.7).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    BigDecimal price,
    int stockQuantity,
    long likeCount,
    Long rankingRank
) {
}
