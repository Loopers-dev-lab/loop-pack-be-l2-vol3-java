package com.loopers.domain.ranking;

import java.util.List;

public interface MvProductRankRepository {

    List<RankingEntry> findByPeriodKey(RankPeriodType type, String periodKey, long offset, long size);

    long countByPeriodKey(RankPeriodType type, String periodKey);
}
