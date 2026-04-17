package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    List<MvProductRankMonthlyEntity> findByPeriodKeyOrderByRankValueAsc(String periodKey);

    void deleteByPeriodKey(String periodKey);
}
