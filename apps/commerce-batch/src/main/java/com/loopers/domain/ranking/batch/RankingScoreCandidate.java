package com.loopers.domain.ranking.batch;

/**
 * 집계 단계에서 힙에 넣는 상품·점수 후보.
 */
public record RankingScoreCandidate(long productId, double score) {
}
