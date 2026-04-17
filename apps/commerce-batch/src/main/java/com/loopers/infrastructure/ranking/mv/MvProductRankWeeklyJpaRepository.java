package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

    List<MvProductRankWeeklyEntity> findByPeriodKeyOrderByRankValueAsc(String periodKey);

    void deleteByPeriodKey(String periodKey);
}
