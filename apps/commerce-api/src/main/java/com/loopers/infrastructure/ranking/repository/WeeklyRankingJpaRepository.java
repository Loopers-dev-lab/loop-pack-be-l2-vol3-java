package com.loopers.infrastructure.ranking.repository;

import com.loopers.infrastructure.ranking.entity.WeeklyRankingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyRankingJpaRepository extends JpaRepository<WeeklyRankingEntity, WeeklyRankingEntity.WeeklyRankingId> {
    List<WeeklyRankingEntity> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);
    long countByPeriodKey(String periodKey);
}
