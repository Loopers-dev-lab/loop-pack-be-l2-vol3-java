package com.loopers.domain.metrics;

/**
 * product_metrics.like_count 불일치 감지 결과.
 *
 * @param productId     상품 ID
 * @param metricsCount  product_metrics 테이블의 like_count
 * @param productsCount products 테이블의 like_count (SSOT)
 */
public record MetricsLikeCountMismatch(Long productId, long metricsCount, long productsCount) {}
