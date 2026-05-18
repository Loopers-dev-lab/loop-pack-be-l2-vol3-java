package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByAggregatedAtOrderByRankAsc(LocalDate aggregatedAt);

    @Modifying
    @Query("DELETE FROM MvProductRankMonthly m WHERE m.aggregatedAt = :aggregatedAt")
    void deleteByAggregatedAt(@Param("aggregatedAt") LocalDate aggregatedAt);
}
