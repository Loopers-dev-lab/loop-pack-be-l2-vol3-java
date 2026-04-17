package com.loopers.infrastructure.ranking.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankStagingJpaRepository extends JpaRepository<MvProductRankStagingEntity, Long> {

    void deleteByPeriodTypeAndPeriodKey(String periodType, String periodKey);

    List<MvProductRankStagingEntity> findByPeriodTypeAndPeriodKeyOrderByRankValueAsc(String periodType, String periodKey);
}
