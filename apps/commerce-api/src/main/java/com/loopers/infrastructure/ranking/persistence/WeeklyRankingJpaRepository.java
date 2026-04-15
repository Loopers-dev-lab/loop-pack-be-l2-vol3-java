package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.ranking.ProductRankingWeekly;

public interface WeeklyRankingJpaRepository extends JpaRepository<ProductRankingWeekly, Long> {

    List<ProductRankingWeekly> findByScoreDateOrderByScoreDesc(LocalDate scoreDate, Pageable pageable);
}
