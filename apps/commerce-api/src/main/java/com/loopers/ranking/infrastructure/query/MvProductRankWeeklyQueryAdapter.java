package com.loopers.ranking.infrastructure.query;


import com.loopers.ranking.application.dto.out.RankingItemOutDto;
import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.query.MvProductRankWeeklyQueryPort;
import com.loopers.ranking.infrastructure.entity.MvProductRankWeeklyEntity;
import com.loopers.ranking.infrastructure.jpa.MvProductRankWeeklyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;


/**
 * 주간 랭킹 MV 조회 QueryPort 구현체
 * - MV 단일 테이블 조회 (JOIN 없음)
 * - page는 0-based (Controller → Facade → Service → 여기까지 0-based 일관성)
 */

@Repository
@RequiredArgsConstructor
public class MvProductRankWeeklyQueryAdapter implements MvProductRankWeeklyQueryPort {

	// jpa
	private final MvProductRankWeeklyJpaRepository jpaRepository;


	@Override
	public RankingPageOutDto findByWeekStartDate(LocalDate weekStartDate, String scorerType, int page, int size) {
		// JPA PageRequest는 0-based
		PageRequest pageable = PageRequest.of(page, size);

		Page<MvProductRankWeeklyEntity> pageResult = jpaRepository
			.findByWeekStartDateAndScorerTypeOrderByRankPositionAsc(weekStartDate, scorerType, pageable);

		List<RankingItemOutDto> items = pageResult.getContent().stream()
			.map(e -> new RankingItemOutDto(
				e.getRankPosition(),
				e.getProductId(),
				e.getProductName(),
				e.getPrice(),
				e.getBrandName(),
				e.getScore().doubleValue()
			))
			.toList();

		long totalElements = jpaRepository.countByWeekStartDateAndScorerType(weekStartDate, scorerType);

		return new RankingPageOutDto(items, page, size, totalElements);
	}

}
