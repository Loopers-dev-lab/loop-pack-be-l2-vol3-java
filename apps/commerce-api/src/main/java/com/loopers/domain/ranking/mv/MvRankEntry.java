package com.loopers.domain.ranking.mv;

/**
 * MV 조회 결과의 최소 표현 — product_id + score + rank_position.
 * 나머지 컬럼(view/like/sales) 은 API 응답에 필요하지 않으므로 투영하지 않는다.
 */
public record MvRankEntry(Long productId, double score, int rankPosition) {
}
