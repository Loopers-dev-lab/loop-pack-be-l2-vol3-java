package com.loopers.batch.job.ranking.common;

/**
 * Reader가 {@code GROUP BY product_id}로 집계한 한 상품의 기간 메트릭.
 * Processor 입력.
 */
public record ProductAggregate(
        long productId,
        long viewCount,
        long likeCount,
        long orderCount,
        long orderAmount
) {
}
