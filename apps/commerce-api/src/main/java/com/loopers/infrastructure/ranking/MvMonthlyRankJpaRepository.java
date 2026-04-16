package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvMonthlyRankId;
import com.loopers.domain.ranking.MvMonthlyRankModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MvMonthlyRankJpaRepository extends JpaRepository<MvMonthlyRankModel, MvMonthlyRankId> {

    List<MvMonthlyRankModel> findByYearMonthOrderByRankNoAsc(String yearMonth, Pageable pageable);

    long countByYearMonth(String yearMonth);

    @Query("SELECT MAX(m.aggregatedAt) FROM MvMonthlyRankModel m WHERE m.yearMonth = :yearMonth")
    Optional<LocalDateTime> findAggregatedAt(@Param("yearMonth") String yearMonth);
}
