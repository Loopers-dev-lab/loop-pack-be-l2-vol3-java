package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.staging.StagingRankingScored;
import com.loopers.domain.ranking.staging.StagingRankingScoredId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StagingRankingScoredJpaRepository
        extends JpaRepository<StagingRankingScored, StagingRankingScoredId> {

    // Command
    @Modifying
    @Query("DELETE FROM StagingRankingScored s WHERE s.periodKey = :periodKey")
    int deleteByPeriodKey(@Param("periodKey") String periodKey);

    // Query
    long countByPeriodKey(String periodKey);
}
