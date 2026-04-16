package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankWeeklyModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyRankingJpaRepository extends JpaRepository<ProductRankWeeklyModel, Long> {

    Page<ProductRankWeeklyModel> findByYearWeekOrderByRankNumberAsc(String yearWeek, Pageable pageable);
}
