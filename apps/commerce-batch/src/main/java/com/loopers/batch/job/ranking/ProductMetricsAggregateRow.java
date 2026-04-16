package com.loopers.batch.job.ranking;

/**
 * JdbcCursorItemReader 가 읽어온 집계 행 (product_id, like_count 합계, order_count 합계, score).
 * score 는 DB에서 직접 계산해 단일 출처로 관리한다.
 */
public record ProductMetricsAggregateRow(
    long productId,
    int likeCount,
    int orderCount,
    double score
) {
}
