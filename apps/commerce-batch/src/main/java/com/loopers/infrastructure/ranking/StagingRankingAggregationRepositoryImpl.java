package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StagingRankingAggregationRepositoryImpl implements StagingRankingAggregationRepository {

    private final StagingRankingAggregationJpaRepository jpaRepository;

    @Override
    public StagingRankingAggregation save(StagingRankingAggregation entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public int deleteByPeriodKey(String periodKey) {
        return jpaRepository.deleteByPeriodKey(periodKey);
    }

    @Override
    public long countByPeriodKey(String periodKey) {
        return jpaRepository.countByPeriodKey(periodKey);
    }
}
