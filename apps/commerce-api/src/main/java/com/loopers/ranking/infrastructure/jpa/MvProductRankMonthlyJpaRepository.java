package com.loopers.ranking.infrastructure.jpa;


import com.loopers.ranking.infrastructure.entity.MvProductRankMonthlyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;


/**
 * 월간 랭킹 MV JPA 레포지토리
 */
public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

	Page<MvProductRankMonthlyEntity> findByMonthStartDateAndScorerTypeOrderByRankPositionAsc(
		LocalDate monthStartDate, String scorerType, Pageable pageable
	);

	long countByMonthStartDateAndScorerType(LocalDate monthStartDate, String scorerType);

}
