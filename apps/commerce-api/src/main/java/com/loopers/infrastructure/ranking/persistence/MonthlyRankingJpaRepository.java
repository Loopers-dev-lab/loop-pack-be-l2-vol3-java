package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.ranking.ProductRankingMonthly;

public interface MonthlyRankingJpaRepository extends JpaRepository<ProductRankingMonthly, Long> {

    List<ProductRankingMonthly> findByScoreDateOrderByScoreDesc(LocalDate scoreDate, Pageable pageable);
}
