package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, MvProductRankWeeklyId> {

    @Modifying
    @Query("DELETE FROM MvProductRankWeekly m WHERE m.weekStartDate = :weekStartDate")
    void deleteByWeekStartDate(@Param("weekStartDate") LocalDate weekStartDate);
}
