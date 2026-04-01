package com.loopers.domain.common.event;

/**
 * 상품 조회 이벤트 — "유저가 상품 상세를 조회했다"
 *
 * 발행 시점: 상품 조회 API 호출 시 (TX 없이 발행 가능)
 * 소비자:
 *   - product_metrics 집계 (조회 수 upsert)
 *   - 유저 행동 로깅 (분석용)
 */
public record ProductViewedEvent(
        Long userId,
        Long productId
) {
}
