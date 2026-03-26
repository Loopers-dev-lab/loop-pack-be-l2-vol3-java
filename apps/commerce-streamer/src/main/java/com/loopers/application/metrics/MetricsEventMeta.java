package com.loopers.application.metrics;

import com.loopers.domain.metrics.MetricsEventType;

/**
 * 상품 지표 이벤트의 메타데이터.
 *
 * @param eventId   이벤트 고유 ID (중복 필터링 기준)
 * @param eventType 이벤트 타입
 */
public record MetricsEventMeta(String eventId, MetricsEventType eventType) {
}
