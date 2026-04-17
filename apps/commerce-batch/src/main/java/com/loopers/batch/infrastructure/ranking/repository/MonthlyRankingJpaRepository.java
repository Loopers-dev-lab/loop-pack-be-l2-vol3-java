package com.loopers.batch.infrastructure.ranking.repository;

import com.loopers.batch.infrastructure.ranking.entity.MonthlyRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyRankingJpaRepository extends JpaRepository<MonthlyRankingEntity, MonthlyRankingEntity.MonthlyRankingId> {

    void deleteByPeriodKey(String periodKey);

    List<MonthlyRankingEntity> findByPeriodKeyOrderByRankNoAsc(String periodKey);
}
