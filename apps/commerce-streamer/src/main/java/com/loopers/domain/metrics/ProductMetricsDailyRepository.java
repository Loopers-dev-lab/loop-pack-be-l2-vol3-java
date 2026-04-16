package com.loopers.domain.metrics;

import java.time.LocalDate;

/**
 * 일별 상품 지표 리포지토리 인터페이스.
 *
 * <p>누적 테이블과 독립적으로 관리하되, Consumer는 두 리포지토리를 같은
 * 트랜잭션 경계에서 호출하여 일관성을 보장한다.</p>
 */
public interface ProductMetricsDailyRepository {

    void incrementViewCountBy(Long productId, LocalDate metricDate, int count);

    void incrementLikeCount(Long productId, LocalDate metricDate);

    void decrementLikeCount(Long productId, LocalDate metricDate);

    void incrementOrderCount(Long productId, LocalDate metricDate, long amount);
}
