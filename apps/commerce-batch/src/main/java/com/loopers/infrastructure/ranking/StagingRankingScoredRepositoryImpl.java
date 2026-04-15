package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.staging.StagingRankingScored;
import com.loopers.domain.ranking.staging.StagingRankingScoredRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StagingRankingScoredRepositoryImpl implements StagingRankingScoredRepository {

    private final StagingRankingScoredJpaRepository jpaRepository;

    @Override
    public StagingRankingScored save(StagingRankingScored entity) {
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
