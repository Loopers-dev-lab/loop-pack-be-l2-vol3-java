package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankWeekly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeekly, Long> {

    List<ProductRankWeekly> findByYearWeekOrderByRankPosition(String yearWeek);

    long countByYearWeek(String yearWeek);
}
