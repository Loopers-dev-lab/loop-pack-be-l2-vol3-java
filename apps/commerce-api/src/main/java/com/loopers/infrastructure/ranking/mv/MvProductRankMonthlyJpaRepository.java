package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.MvProductRankMonthly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByAggregatedAtOrderByRankAsc(LocalDate aggregatedAt, Pageable pageable);
}
