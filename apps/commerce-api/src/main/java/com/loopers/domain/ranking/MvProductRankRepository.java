package com.loopers.domain.ranking;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface MvProductRankRepository {

    List<RankingEntry> findByPeriodKey(RankPeriodType type, String periodKey, long offset, long size);

    long countByPeriodKey(RankPeriodType type, String periodKey);

    Optional<ZonedDateTime> findLastUpdatedAt(RankPeriodType type, String periodKey);

    Optional<Long> findPublishedVersion(RankPeriodType type, String periodKey);
}
