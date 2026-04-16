package com.loopers.domain.ranking;

/**
 * MV 랭킹 테이블 적재 단위 VO.
 *
 * ItemWriter 가 Chunk 의 순서를 기반으로 rank 를 1부터 순차 할당한 뒤 생성한다.
 * Reader SQL 이 이미 score 내림차순으로 정렬되어 있으므로,
 * Chunk 순서(0, 1, 2 ...) = rank(1, 2, 3 ...) 가 보장된다.
 *
 * mv_product_rank_weekly 와 mv_product_rank_monthly 양쪽에 공통으로 사용된다.
 *
 * @param productId 상품 ID
 * @param rank      1-based 랭킹 순위
 * @param score     집계 기간 내 가중치 합산 점수
 */
public record MvProductRankRow(
        Long productId,
        int rank,
        double score
) {
}
