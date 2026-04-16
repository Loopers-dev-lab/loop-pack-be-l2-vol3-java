package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyRankingJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    @Query("SELECT w.productId FROM MvProductRankWeekly w WHERE w.baseDate = :baseDate ORDER BY w.rank ASC")
    List<Long> findProductIdsByBaseDate(@Param("baseDate") LocalDate baseDate, Pageable pageable);

    long countByBaseDate(LocalDate baseDate);
}
