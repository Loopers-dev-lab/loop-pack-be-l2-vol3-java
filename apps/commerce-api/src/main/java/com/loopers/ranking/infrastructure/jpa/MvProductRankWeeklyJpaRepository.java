package com.loopers.ranking.infrastructure.jpa;


import com.loopers.ranking.infrastructure.entity.MvProductRankWeeklyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;


/**
 * 주간 랭킹 MV JPA 레포지토리
 */
public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

	Page<MvProductRankWeeklyEntity> findByWeekStartDateAndScorerTypeOrderByRankPositionAsc(
		LocalDate weekStartDate, String scorerType, Pageable pageable
	);

	long countByWeekStartDateAndScorerType(LocalDate weekStartDate, String scorerType);

}
