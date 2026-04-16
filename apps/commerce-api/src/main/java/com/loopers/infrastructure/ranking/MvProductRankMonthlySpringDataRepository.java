package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlySpringDataRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByPeriodKeyOrderByRankingAsc(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}
