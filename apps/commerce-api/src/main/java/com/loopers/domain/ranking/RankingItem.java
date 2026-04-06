package com.loopers.domain.ranking;

/**
 * 랭킹 Sorted Set의 단일 항목.
 *
 * @param rank      1-based 순위
 * @param productId 상품 ID
 * @param score     가중치 기반 누적 점수
 */
public record RankingItem(int rank, Long productId, double score) {
}
