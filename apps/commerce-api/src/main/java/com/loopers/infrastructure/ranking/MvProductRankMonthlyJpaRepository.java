package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 월간 랭킹 MV 읽기 전용 Repository.
 * 구조는 주간과 동일, 키만 period_month.
 */
public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    List<MvProductRankMonthlyEntity> findByPeriodMonthAndRankNoBetweenOrderByRankNoAsc(
            String periodMonth, int fromRank, int toRank);

    long countByPeriodMonth(String periodMonth);
}
