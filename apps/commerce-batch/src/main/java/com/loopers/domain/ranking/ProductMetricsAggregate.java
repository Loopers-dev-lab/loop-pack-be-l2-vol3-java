package com.loopers.domain.ranking;

/**
 * product_metrics_hourly 집계 쿼리 결과 VO.
 *
 * JdbcCursorItemReader 의 읽기 단위(Item)이다.
 * SQL 레벨에서 슬라이딩 윈도우 기간의 집계와 가중치 점수 계산이 이미 완료되어 있으므로
 * 이 VO 는 단순히 그 결과를 담는 역할만 한다.
 *
 * SQL 점수 계산 공식 (일간 랭킹 RankingScoreCalculator 와 동일):
 *   LN(1 + SUM(view_count))    * weightView
 *   + LN(1 + SUM(like_count))  * weightLike
 *   + LN(1 + SUM(order_amount)) * weightOrder
 *
 * Reader 는 score 내림차순으로 결과를 반환하므로,
 * Chunk 내 순서가 곧 랭킹 순서를 의미한다.
 *
 * @param productId 상품 ID
 * @param score     슬라이딩 윈도우 기간 내 가중치 합산 점수
 */
public record ProductMetricsAggregate(
        Long productId,
        double score
) {
}
