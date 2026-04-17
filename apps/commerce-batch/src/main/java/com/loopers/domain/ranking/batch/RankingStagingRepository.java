package com.loopers.domain.ranking.batch;

import java.util.List;

/**
 * 랭킹 스테이징 MV 적재 포트.
 */
public interface RankingStagingRepository {

    void deleteByPeriodTypeAndPeriodKey(String periodType, String periodKey);

    void saveRankedRows(String periodType, String periodKey, List<RankingStagingRankRow> rows);

    /**
     * publish 직전 조회. rank 오름차순.
     */
    List<RankingStagingRankRow> findRankedRows(String periodType, String periodKey);
}
