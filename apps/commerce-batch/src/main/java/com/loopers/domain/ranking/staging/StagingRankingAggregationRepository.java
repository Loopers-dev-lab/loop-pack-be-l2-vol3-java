package com.loopers.domain.ranking.staging;

public interface StagingRankingAggregationRepository {

    // Command
    StagingRankingAggregation save(StagingRankingAggregation entity);

    int deleteByPeriodKey(String periodKey);

    // Query
    long countByPeriodKey(String periodKey);
}
