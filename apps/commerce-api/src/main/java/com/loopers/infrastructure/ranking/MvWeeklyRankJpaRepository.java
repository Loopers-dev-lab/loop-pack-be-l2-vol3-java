package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvWeeklyRankId;
import com.loopers.domain.ranking.MvWeeklyRankModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MvWeeklyRankJpaRepository extends JpaRepository<MvWeeklyRankModel, MvWeeklyRankId> {

    List<MvWeeklyRankModel> findByBaseDateOrderByRankNoAsc(LocalDate baseDate, Pageable pageable);

    long countByBaseDate(LocalDate baseDate);

    @Query("SELECT MAX(m.aggregatedAt) FROM MvWeeklyRankModel m WHERE m.baseDate = :baseDate")
    Optional<LocalDateTime> findAggregatedAt(@Param("baseDate") LocalDate baseDate);
}
