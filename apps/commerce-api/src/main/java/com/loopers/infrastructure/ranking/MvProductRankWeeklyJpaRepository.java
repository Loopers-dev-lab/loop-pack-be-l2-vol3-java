package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    @Query("SELECT m FROM MvProductRankWeekly m WHERE m.periodStart <= :date AND m.periodEnd >= :date ORDER BY m.rankPosition ASC")
    List<MvProductRankWeekly> findByDate(@Param("date") LocalDate date, Pageable pageable);

    @Modifying
    @Query("DELETE FROM MvProductRankWeekly m WHERE m.periodStart = :periodStart AND m.periodEnd = :periodEnd")
    void deleteByPeriod(@Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
}
