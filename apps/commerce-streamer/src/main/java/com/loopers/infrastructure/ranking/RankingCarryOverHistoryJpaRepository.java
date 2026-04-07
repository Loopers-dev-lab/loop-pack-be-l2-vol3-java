package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingCarryOverHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface RankingCarryOverHistoryJpaRepository extends JpaRepository<RankingCarryOverHistory, Long> {
    boolean existsByCarryOverDate(LocalDate date);
}
