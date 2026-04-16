package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyModel, Long> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM MvProductRankMonthlyModel m WHERE m.yearMonth = :yearMonth")
    void deleteByYearMonth(@Param("yearMonth") String yearMonth);
}
