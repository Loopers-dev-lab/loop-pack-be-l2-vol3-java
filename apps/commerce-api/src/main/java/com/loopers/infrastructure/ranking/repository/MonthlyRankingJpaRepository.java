package com.loopers.infrastructure.ranking.repository;

import com.loopers.infrastructure.ranking.entity.MonthlyRankingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyRankingJpaRepository extends JpaRepository<MonthlyRankingEntity, MonthlyRankingEntity.MonthlyRankingId> {
    List<MonthlyRankingEntity> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);
    long countByPeriodKey(String periodKey);
}
