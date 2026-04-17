package com.loopers.batch.infrastructure.ranking.repository;

import com.loopers.batch.infrastructure.ranking.entity.WeeklyRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyRankingJpaRepository extends JpaRepository<WeeklyRankingEntity, WeeklyRankingEntity.WeeklyRankingId> {

    void deleteByPeriodKey(String periodKey);

    List<WeeklyRankingEntity> findByPeriodKeyOrderByRankNoAsc(String periodKey);
}
