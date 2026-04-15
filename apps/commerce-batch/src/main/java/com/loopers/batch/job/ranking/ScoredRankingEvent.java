package com.loopers.batch.job.ranking;

/**
 * Processor 출력: 이벤트 1건의 상품 ID와 계산된 점수 delta.
 * Writer에서 productId 키로 Map에 누적된다.
 */
public record ScoredRankingEvent(long productId, double delta) {}
