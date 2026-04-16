package com.loopers.ranking.infrastructure.query;


import com.loopers.ranking.application.dto.out.RankingItemOutDto;
import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.query.MvProductRankMonthlyQueryPort;
import com.loopers.ranking.infrastructure.entity.MvProductRankMonthlyEntity;
import com.loopers.ranking.infrastructure.jpa.MvProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;


/**
 * 월간 랭킹 MV 조회 QueryPort 구현체
 * - page는 0-based
 */

@Repository
@RequiredArgsConstructor
public class MvProductRankMonthlyQueryAdapter implements MvProductRankMonthlyQueryPort {

	// jpa
	private final MvProductRankMonthlyJpaRepository jpaRepository;


	@Override
	public RankingPageOutDto findByMonthStartDate(LocalDate monthStartDate, String scorerType, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size);

		Page<MvProductRankMonthlyEntity> pageResult = jpaRepository
			.findByMonthStartDateAndScorerTypeOrderByRankPositionAsc(monthStartDate, scorerType, pageable);

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

		long totalElements = jpaRepository.countByMonthStartDateAndScorerType(monthStartDate, scorerType);

		return new RankingPageOutDto(items, page, size, totalElements);
	}

}
