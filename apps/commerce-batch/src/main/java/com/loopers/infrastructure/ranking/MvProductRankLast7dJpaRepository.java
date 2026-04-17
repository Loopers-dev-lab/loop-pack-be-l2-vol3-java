package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.mv.MvProductRankId;
import com.loopers.domain.ranking.mv.MvProductRankLast7d;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

interface MvProductRankLast7dJpaRepository
        extends JpaRepository<MvProductRankLast7d, MvProductRankId> {

    // Command
    @Modifying
    @Query("DELETE FROM MvProductRankLast7d m WHERE m.anchorDate = :anchorDate")
    int deleteByAnchorDate(@Param("anchorDate") LocalDate anchorDate);

    // Query
    long countByAnchorDate(LocalDate anchorDate);
}
