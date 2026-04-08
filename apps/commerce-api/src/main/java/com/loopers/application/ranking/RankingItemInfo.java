package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

/**
 * 랭킹 항목 Application DTO
 *
 * ZSET 랭킹 데이터 + 상품 정보 + 브랜드명을 조합한 결과.
 * Controller에서 RankingResponse DTO로 변환된다.
 */
public record RankingItemInfo(
        int rank,
        ProductInfo product,
        String brandName,
        double score
) {}
