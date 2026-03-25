package com.loopers.domain.common.event;

/**
 * 상품 좋아요 이벤트 — "유저가 상품을 좋아요했다"
 *
 * 발행 시점: 좋아요 TX 커밋 이후 (AFTER_COMMIT)
 * 소비자: product_metrics 집계 (현재 ApplicationEvent, 추후 Kafka 전환)
 *
 * 같은 TX에 묶인 것 (이벤트로 분리 ❌):
 *   좋아요 저장 (ProductLike) + 좋아요 수 증가 (atomic UPDATE)
 *   → 좋아요 수가 즉시 반영돼야 하므로 같은 TX
 *
 * 이벤트로 분리한 것 (✅):
 *   product_metrics 집계 — eventual consistency 허용
 */
public record ProductLikedEvent(
        Long userId,
        Long productId,
        boolean liked  // true=좋아요, false=좋아요 취소
) {
}
