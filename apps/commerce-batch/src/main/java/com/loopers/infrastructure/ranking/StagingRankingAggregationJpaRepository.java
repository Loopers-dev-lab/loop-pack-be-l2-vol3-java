package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingAggregationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StagingRankingAggregationJpaRepository
        extends JpaRepository<StagingRankingAggregation, StagingRankingAggregationId> {

    // Command
    @Modifying
    @Query("DELETE FROM StagingRankingAggregation s WHERE s.periodKey = :periodKey")
    int deleteByPeriodKey(@Param("periodKey") String periodKey);

    // Query
    long countByPeriodKey(String periodKey);
}
