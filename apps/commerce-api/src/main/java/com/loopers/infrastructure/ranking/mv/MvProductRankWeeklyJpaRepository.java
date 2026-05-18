package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.MvProductRankWeekly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    List<MvProductRankWeekly> findByAggregatedAtOrderByRankAsc(LocalDate aggregatedAt, Pageable pageable);
}
