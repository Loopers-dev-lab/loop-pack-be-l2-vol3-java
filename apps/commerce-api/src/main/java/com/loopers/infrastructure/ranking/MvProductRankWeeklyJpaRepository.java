package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, MvProductRankWeeklyId> {

    List<MvProductRankWeekly> findByYearWeekOrderByRankingAsc(String yearWeek, Pageable pageable);

    List<MvProductRankWeekly> findByYearWeekOrderByRankingAsc(String yearWeek);
}
