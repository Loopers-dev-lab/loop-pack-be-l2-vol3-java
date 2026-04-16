package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMonthly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthly, Long> {

    List<ProductRankMonthly> findByYearMonthOrderByRankPosition(String yearMonth);

    long countByYearMonth(String yearMonth);
}
