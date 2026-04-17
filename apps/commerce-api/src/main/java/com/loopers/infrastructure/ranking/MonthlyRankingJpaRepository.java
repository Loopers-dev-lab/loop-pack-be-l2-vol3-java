package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyRankingJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    @Query("SELECT m.productId FROM MvProductRankMonthly m WHERE m.baseDate = :baseDate ORDER BY m.rank ASC")
    List<Long> findProductIdsByBaseDate(@Param("baseDate") LocalDate baseDate, Pageable pageable);

    long countByBaseDate(LocalDate baseDate);
}
