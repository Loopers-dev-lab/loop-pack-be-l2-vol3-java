package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, MvProductRankMonthlyId> {

    @Modifying
    @Query("DELETE FROM MvProductRankMonthly m WHERE m.monthStartDate = :monthStartDate")
    void deleteByMonthStartDate(@Param("monthStartDate") LocalDate monthStartDate);
}
