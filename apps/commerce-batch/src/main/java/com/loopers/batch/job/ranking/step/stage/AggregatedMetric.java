package com.loopers.batch.job.ranking.step.stage;

/**
 * App streaming aggregator 가 product_id 경계마다 flush 하는 집계 결과.
 * LAST_7D/LAST_30D 범위의 합산이 한 번의 cursor 스캔으로 동시에 계산된다.
 */
public record AggregatedMetric(Long productId, long sum7d, long sum30d) {
}
