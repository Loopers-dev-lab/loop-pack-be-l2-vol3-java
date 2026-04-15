package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, MvProductRankMonthlyId> {

    List<MvProductRankMonthly> findByYearMonthOrderByRankingAsc(String yearMonth, Pageable pageable);

    List<MvProductRankMonthly> findByYearMonthOrderByRankingAsc(String yearMonth);
}
