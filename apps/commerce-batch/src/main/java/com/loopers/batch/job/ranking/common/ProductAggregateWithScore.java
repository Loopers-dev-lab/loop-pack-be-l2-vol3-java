package com.loopers.batch.job.ranking.common;

import java.math.BigDecimal;

/**
 * Processor가 score를 부여한 집계 결과. Writer 입력.
 *
 * <p>Writer의 beanMapped 접근을 위해 record의 접근자 이름(productId 등)과
 * SQL 바인드 파라미터 이름을 일치시킨다.</p>
 */
public record ProductAggregateWithScore(
        long productId,
        long viewCount,
        long likeCount,
        long orderCount,
        long orderAmount,
        BigDecimal score
) {
    public static ProductAggregateWithScore of(ProductAggregate agg, BigDecimal score) {
        return new ProductAggregateWithScore(
                agg.productId(),
                agg.viewCount(),
                agg.likeCount(),
                agg.orderCount(),
                agg.orderAmount(),
                score
        );
    }
}
