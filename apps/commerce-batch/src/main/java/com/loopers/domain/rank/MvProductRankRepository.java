package com.loopers.domain.rank;

import java.util.List;

public interface MvProductRankRepository {

    void deleteByPeriodKey(RankPeriodType type, String periodKey);

    void batchInsert(RankPeriodType type, List<MvProductRankRow> rows);

    List<MvProductRankRow> findByPeriodKey(RankPeriodType type, String periodKey, long offset, long size);

    long countByPeriodKey(RankPeriodType type, String periodKey);
}
