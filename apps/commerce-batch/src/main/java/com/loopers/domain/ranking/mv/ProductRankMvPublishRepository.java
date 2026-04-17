package com.loopers.domain.ranking.mv;

import java.time.Instant;
import java.util.List;

/**
 * 주간/월간 MV를 스테이징 검증 이후 원자적으로 교체한다(Option A publish).
 */
public interface ProductRankMvPublishRepository {

    void replaceWeeklyPeriod(String periodKey, List<ProductRankMvRow> rows, Instant publishedAt);

    void replaceMonthlyPeriod(String periodKey, List<ProductRankMvRow> rows, Instant publishedAt);
}
