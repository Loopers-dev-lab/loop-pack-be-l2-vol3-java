package com.loopers.domain.ranking.staging;

public interface StagingRankingScoredRepository {

    // Command
    StagingRankingScored save(StagingRankingScored entity);

    int deleteByPeriodKey(String periodKey);

    // Query
    long countByPeriodKey(String periodKey);
}
