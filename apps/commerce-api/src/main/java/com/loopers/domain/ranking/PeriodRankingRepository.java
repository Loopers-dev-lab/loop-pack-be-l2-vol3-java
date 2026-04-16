package com.loopers.domain.ranking;

import java.util.List;

public interface PeriodRankingRepository {

    /**
     * 특정 기간 키(예: "2026-W15", "2026-04") 의 랭킹을 offset/limit 기반으로 조회한다.
     *
     * @param periodKey 주간이면 "2026-W15", 월간이면 "2026-04"
     * @param offset    건너뛸 row 수 (0부터 시작)
     * @param limit     가져올 row 수
     */
    List<ProductRanking> findTopN(String periodKey, long offset, int limit);

    /**
     * 해당 기간에 저장된 전체 row 수. MV 는 TOP 100 만 저장되므로 최대 100.
     */
    long countByPeriod(String periodKey);
}
