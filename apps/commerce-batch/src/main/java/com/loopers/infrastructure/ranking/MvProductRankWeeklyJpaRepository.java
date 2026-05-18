package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    List<MvProductRankWeekly> findByAggregatedAtOrderByRankAsc(LocalDate aggregatedAt);

    @Modifying
    @Query("DELETE FROM MvProductRankWeekly m WHERE m.aggregatedAt = :aggregatedAt")
    void deleteByAggregatedAt(@Param("aggregatedAt") LocalDate aggregatedAt);
}
