package com.loopers.domain.ranking;

import java.math.BigDecimal;

/**
 * 랭킹 목록 (도메인 조회 결과 형태 고정)
 *
 * @param rank 랭킹 순위
 * @param productId 상품 ID
 * @param score 랭킹 점수
 * @param productName 상품 이름
 * @param price 상품 가격
 * @param brandId 브랜드 ID
 * @param brandName 브랜드 이름
 * @param likeCount 좋아요 수
 */
public record RankingRow(
        int rank,
        long productId,
        double score,
        String productName,
        BigDecimal price,
        long brandId,
        String brandName,
        long likeCount
) {
}
