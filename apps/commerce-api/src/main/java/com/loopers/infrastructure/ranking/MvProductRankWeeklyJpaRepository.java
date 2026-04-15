package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, MvProductRankWeeklyEntity.PK> {

    List<MvProductRankWeeklyEntity> findByWeekStartDateOrderByRankPositionAsc(LocalDate weekStartDate, Pageable pageable);
}
