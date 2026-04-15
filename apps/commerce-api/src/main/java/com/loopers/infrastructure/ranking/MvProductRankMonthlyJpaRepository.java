package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, MvProductRankMonthlyEntity.PK> {

    List<MvProductRankMonthlyEntity> findByMonthStartDateOrderByRankPositionAsc(LocalDate monthStartDate, Pageable pageable);
}
