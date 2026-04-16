package com.loopers.ranking.application.facade;


import com.loopers.ranking.application.dto.out.RankedResult;
import com.loopers.ranking.application.dto.out.RankingItemOutDto;
import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.client.catalog.RankingProductInfo;
import com.loopers.ranking.application.service.MonthlyRankingQueryService;
import com.loopers.ranking.application.service.RankingQueryService;
import com.loopers.ranking.application.service.RankingRankService;
import com.loopers.ranking.application.service.WeeklyRankingQueryService;
import com.loopers.ranking.domain.model.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 랭킹 조회 파사드
 * - period 파라미터로 daily / weekly / monthly 분기
 * - daily: 기존 Redis ZSET 기반 (RankingQueryService)
 * - weekly: mv_product_rank_weekly 기반 (WeeklyRankingQueryService)
 * - monthly: mv_product_rank_monthly 기반 (MonthlyRankingQueryService)
 * - rank 단건 조회는 RankingRankFacade로 분리 (순환참조 방지)
 *
 * 1. 기간별 랭킹 목록 조회 (period 분기)
 * 2. 특정 상품 랭킹 순위 조회 (daily 전용)
 */

@Service
@RequiredArgsConstructor
public class RankingQueryFacade {

	// service
	private final RankingQueryService rankingQueryService;
	private final RankingRankService rankingRankService;
	private final WeeklyRankingQueryService weeklyRankingQueryService;
	private final MonthlyRankingQueryService monthlyRankingQueryService;


	// 1. 기간별 랭킹 목록 조회 (period 분기)
	@Transactional(readOnly = true)
	public RankingPageOutDto getRankings(RankingPeriod period, String dateStr, int page, int size) {
		return switch (period) {
			case DAILY -> getDailyRankings(dateStr, page, size);
			case WEEKLY -> weeklyRankingQueryService.getRankings(dateStr, page, size);
			case MONTHLY -> monthlyRankingQueryService.getRankings(dateStr, page, size);
		};
	}

	/**
	 * 기존 호환 메서드 — period 없이 호출 시 DAILY로 처리
	 * - Controller 기존 호출 경로 유지 (하위 호환)
	 */
	@Transactional(readOnly = true)
	public RankingPageOutDto getRankings(String dateStr, int page, int size) {
		return getDailyRankings(dateStr, page, size);
	}


	// 2. 특정 상품 랭킹 순위 조회 (RankingRankService에 위임)
	@Transactional(readOnly = true)
	public Long getProductRank(String dateStr, Long productId) {
		return rankingRankService.getProductRank(dateStr, productId);
	}


	/**
	 * private method
	 * - 일간 랭킹 조회 (Redis ZSET + 상품 정보 조합)
	 */

	// 일간 랭킹 조회 (Redis ZSET + 상품 정보 조합, 0-based page 유지)
	private RankingPageOutDto getDailyRankings(String dateStr, int page, int size) {
		// ZSET 조회 (productId + score 리스트 + totalElements)
		RankedResult rankedResult = rankingQueryService.getRankedProductEntries(dateStr, page, size);
		List<ProductScoreEntry> entries = rankedResult.entries();

		if (entries.isEmpty()) {
			return new RankingPageOutDto(List.of(), page, size, rankedResult.totalElements());
		}

		// 상품 ID 추출
		List<Long> productIds = entries.stream()
			.map(ProductScoreEntry::productId)
			.toList();

		// Cross-BC: 상품 정보 일괄 조회 (Service 경유)
		List<RankingProductInfo> productInfos = rankingQueryService.readProducts(productIds);
		Map<Long, RankingProductInfo> infoMap = productInfos.stream()
			.collect(Collectors.toMap(RankingProductInfo::productId, Function.identity()));

		// 순서 유지하며 join (삭제/비활성 상품은 skip)
		int baseRank = page * size;
		List<RankingItemOutDto> items = new ArrayList<>();
		int index = 0;

		for (ProductScoreEntry entry : entries) {
			RankingProductInfo info = infoMap.get(entry.productId());
			if (info == null) {
				index++;
				continue;
			}
			items.add(new RankingItemOutDto(
				baseRank + index + 1,
				entry.productId(),
				info.name(),
				info.price(),
				info.brandName(),
				entry.score()
			));
			index++;
		}

		return new RankingPageOutDto(items, page, size, rankedResult.totalElements());
	}

}
