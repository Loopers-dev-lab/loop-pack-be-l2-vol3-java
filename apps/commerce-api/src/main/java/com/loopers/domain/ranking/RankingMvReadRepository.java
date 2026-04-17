package com.loopers.domain.ranking;

import java.util.List;

/**
 * 주간/월간 랭킹 MV 읽기 포트. 구현은 infrastructure.
 */
public interface RankingMvReadRepository {

    List<RankingMvTableRow> findWeeklyByPeriodKeyOrdered(String periodKey);

    List<RankingMvTableRow> findMonthlyByPeriodKeyOrdered(String periodKey);
}
