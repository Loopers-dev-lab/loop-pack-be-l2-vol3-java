package com.loopers.batch.job.ranking;

/**
 * Processor 출력 / JdbcBatchItemWriter 입력 행.
 * mv_product_rank_weekly 또는 mv_product_rank_monthly 에 INSERT 된다.
 */
public record MvRankRow(
    long productId,
    int likeCount,
    int orderCount,
    double score,
    String period
) {
}
