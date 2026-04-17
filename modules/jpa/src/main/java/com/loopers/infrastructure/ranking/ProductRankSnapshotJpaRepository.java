package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductRankSnapshotJpaRepository extends JpaRepository<ProductRankSnapshot, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankSnapshot p WHERE p.rankingType = :rankingType AND p.rankDate = :rankDate")
    void deleteByRankingTypeAndRankDate(
            @Param("rankingType") RankingType rankingType,
            @Param("rankDate") LocalDate rankDate
    );
}
