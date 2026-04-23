package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMonthlyModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyRankingJpaRepository extends JpaRepository<ProductRankMonthlyModel, Long> {

    Page<ProductRankMonthlyModel> findByYearMonthOrderByRankNumberAsc(String yearMonth, Pageable pageable);
}
